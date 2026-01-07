package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetricsService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SMA_WINDOW = 20;
    private static final int VOL_WINDOW = 20;

    private final AssetRepository assetRepo;
    private final PriceBarRepository priceRepo;
    private final MetricValueRepository metricRepo;

    public MetricsService(AssetRepository assetRepo, PriceBarRepository priceRepo, MetricValueRepository metricRepo) {
        this.assetRepo = assetRepo;
        this.priceRepo = priceRepo;
        this.metricRepo = metricRepo;
    }

    @Transactional
    public MetricsRunResult computeMetricsForAsset(Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        List<PriceBar> bars = priceRepo.findByAssetIdOrderByTsAsc(assetId);
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
            BigDecimal r = curr.divide(prev, MC).subtract(BigDecimal.ONE, MC);
            returns.add(r);

            LocalDate ts = bars.get(i).getTs();

            inserted += upsertMetric(asset, ts, MetricType.RETURN_1D, r);
        }

        // SMA_20: first day we can compute is i = 19
        for (int i = SMA_WINDOW - 1; i < bars.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i - (SMA_WINDOW - 1); j <= i; j++) {
                sum = sum.add(closes.get(j), MC);
            }
            BigDecimal sma = sum.divide(BigDecimal.valueOf(SMA_WINDOW), MC);
            LocalDate ts = bars.get(i).getTs();
            inserted += upsertMetric(asset, ts, MetricType.SMA_20, sma);
        }

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

    public record MetricsRunResult(Long assetId, String symbol, int inserted, String status) {}
}
