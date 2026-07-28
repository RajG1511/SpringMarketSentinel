package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.api.ApiExceptions;
import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.config.CacheConfig;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class MetricsService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SMA_WINDOW = 20;
    private static final int SMA_LONG_WINDOW = 50;
    private static final int VOL_WINDOW = 20;

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;
    private final MetricValueRepository metricRepo;

    public MetricsService(AssetRepository assetRepo, PriceBarRepository priceRepo, MetricValueRepository metricRepo) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
        this.metricRepo = metricRepo;
    }

    @CacheEvict(cacheNames = CacheConfig.LATEST_METRICS, key = "#assetId")
    @Transactional
    public MetricsRunResult computeMetricsForAsset(Long assetId, int lookbackDays) {

        LocalDate from = LocalDate.now().minusDays(lookbackDays);

        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Asset not found: " + assetId));

        List<PriceBar> bars = priceRepo.findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(assetId, from);
        if (bars.size() < 2) {
            return new MetricsRunResult(assetId, asset.getSymbol(), 0, "Not enough data to compute metrics");
        }

        int inserted = 0;

        // Precompute closes and returns aligned by index
        List<BigDecimal> closes = new ArrayList<>(bars.size());
        for (PriceBar b : bars) closes.add(b.getClose());

        // returns[i] corresponds to bars[i] (return from bars[i-1] to bars[i]); returns[0] is null
        List<BigDecimal> returns = new ArrayList<>(bars.size());
        returns.add(null);

        for (int i = 1; i < bars.size(); i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal curr = closes.get(i);

            // (curr / prev) - 1
            if (prev.signum() == 0) {
                continue; // or set return to null and skip
            }
            BigDecimal r = curr.divide(prev, MC).subtract(BigDecimal.ONE, MC);
            returns.add(r);

            LocalDate ts = bars.get(i).getTs();

            inserted += upsertMetric(asset, ts, MetricType.RETURN_1D, r);
        }

        // Simple moving averages (first computable day is index window-1)
        inserted += computeSma(asset, bars, closes, SMA_WINDOW, MetricType.SMA_20);
        inserted += computeSma(asset, bars, closes, SMA_LONG_WINDOW, MetricType.SMA_50);

        // VOL_20: stddev of last 20 returns (need returns for a full window)
        // returns start at index 1, so first full window ends at i = 20
        for (int i = VOL_WINDOW; i < bars.size(); i++) {
            // window returns: i-(VOL_WINDOW-1) .. i  (20 values)
            List<BigDecimal> window = new ArrayList<>(VOL_WINDOW);
            for (int j = i - (VOL_WINDOW - 1); j <= i; j++) {
                window.add(returns.get(j));
            }

            BigDecimal vol = stdDev(window);
            LocalDate ts = bars.get(i).getTs();
            inserted += upsertMetric(asset, ts, MetricType.VOL_20, vol);
        }

        return new MetricsRunResult(assetId, asset.getSymbol(), inserted, "OK");
    }

    private int computeSma(Asset asset, List<PriceBar> bars, List<BigDecimal> closes, int window, MetricType type) {
        int inserted = 0;
        for (int i = window - 1; i < bars.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i - (window - 1); j <= i; j++) {
                sum = sum.add(closes.get(j), MC);
            }
            BigDecimal sma = sum.divide(BigDecimal.valueOf(window), MC);
            inserted += upsertMetric(asset, bars.get(i).getTs(), type, sma);
        }
        return inserted;
    }

    private int upsertMetric(Asset asset, LocalDate ts, MetricType type, BigDecimal value) {
        // idempotent: only insert if missing
        if (metricRepo.existsByAssetIdAndTsAndMetricType(asset.getId(), ts, type)) {
            return 0;
        }
        metricRepo.save(new MetricValue(asset, ts, type, value));
        return 1;
    }

    // population std dev: sqrt( avg( (x-mean)^2 ) )
    // (For trading, either pop or sample is fine; consistency matters more than choice.)
    private BigDecimal stdDev(List<BigDecimal> xs) {
        BigDecimal mean = BigDecimal.ZERO;
        for (BigDecimal x : xs) mean = mean.add(x, MC);
        mean = mean.divide(BigDecimal.valueOf(xs.size()), MC);

        BigDecimal var = BigDecimal.ZERO;
        for (BigDecimal x : xs) {
            BigDecimal diff = x.subtract(mean, MC);
            var = var.add(diff.multiply(diff, MC), MC);
        }
        var = var.divide(BigDecimal.valueOf(xs.size()), MC);

        // BigDecimal sqrt (simple Newton)
        return sqrt(var);
    }

    private BigDecimal sqrt(BigDecimal x) {
        if (x.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal guess = new BigDecimal(Math.sqrt(x.doubleValue()), MC);
        BigDecimal two = BigDecimal.valueOf(2);

        for (int i = 0; i < 20; i++) {
            guess = guess.add(x.divide(guess, MC), MC).divide(two, MC);
        }
        return guess;
    }

    /**
     * Latest value of each metric type for an asset. Cached in Redis and evicted
     * whenever metrics are recomputed for that asset (see computeMetricsForAsset).
     */
    @Cacheable(cacheNames = CacheConfig.LATEST_METRICS, key = "#assetId")
    @Transactional(readOnly = true)
    public LatestMetrics getLatestMetrics(Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Asset not found: " + assetId));

        Optional<MetricValue> r = metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(assetId, MetricType.RETURN_1D);
        Optional<MetricValue> s20 = metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(assetId, MetricType.SMA_20);
        Optional<MetricValue> s50 = metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(assetId, MetricType.SMA_50);
        Optional<MetricValue> v20 = metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(assetId, MetricType.VOL_20);

        LocalDate asOf = Stream.of(r, s20, s50, v20)
                .filter(Optional::isPresent).map(Optional::get)
                .map(MetricValue::getTs)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new LatestMetrics(
                assetId, asset.getSymbol(), asOf,
                r.map(MetricValue::getValue).orElse(null),
                s20.map(MetricValue::getValue).orElse(null),
                s50.map(MetricValue::getValue).orElse(null),
                v20.map(MetricValue::getValue).orElse(null)
        );
    }

    public record MetricsRunResult(Long assetId, String symbol, int inserted, String status) {}

    public record LatestMetrics(
            Long assetId, String symbol, LocalDate asOf,
            BigDecimal return1d, BigDecimal sma20, BigDecimal sma50, BigDecimal vol20
    ) {}
}
