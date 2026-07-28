package com.raj.springmarketanalysis.alert;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.metric.MetricType;
import com.raj.springmarketanalysis.metric.MetricValue;
import com.raj.springmarketanalysis.metric.MetricValueRepository;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AlertsService {

    private final AssetRepository assetRepo;
    private final AlertRuleRepository ruleRepo;
    private final AlertEventRepository eventRepo;
    private final MetricValueRepository metricRepo;
    private final PriceBarRepository priceRepo;

    public AlertsService(
            AssetRepository assetRepo,
            AlertRuleRepository ruleRepo,
            AlertEventRepository eventRepo,
            MetricValueRepository metricRepo,
            PriceBarRepository priceRepo
    ) {
        this.assetRepo = assetRepo;
        this.ruleRepo = ruleRepo;
        this.eventRepo = eventRepo;
        this.metricRepo = metricRepo;
        this.priceRepo = priceRepo;
    }

    public record AlertRunResult(Long assetId, String symbol, int fired, String status) {}

    @Transactional
    public AlertRunResult evaluateAlertsForAsset(Long assetId) {
        Asset asset = assetRepo.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // Find "latest date" using price bars (anchor)
        PriceBar latestBar = priceRepo.findTopByAssetIdOrderByTsDesc(assetId)
                .orElseThrow(() -> new IllegalStateException("No price data for asset: " + assetId));

        LocalDate ts = latestBar.getTs();

        List<AlertRule> rules = ruleRepo.findByEnabledTrue();
        int fired = 0;

        for (AlertRule rule : rules) {
            switch (rule.getRuleKey()) {
                case "VOL_SPIKE_20" -> fired += evalVolSpike(asset, ts, rule);
                case "DRAWDOWN_10PCT" -> fired += evalDrawdown(asset, ts, rule);
                default -> { /* ignore unknown rules */ }
            }
        }

        return new AlertRunResult(assetId, asset.getSymbol(), fired, "OK");
    }

    private int evalVolSpike(Asset asset, LocalDate ts, AlertRule rule) {
        // Need today's VOL_20
        var latestVolOpt = metricRepo.findByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
                asset.getId(), MetricType.VOL_20, ts
        ).stream().findFirst();

        if (latestVolOpt.isEmpty()) return 0;
        MetricValue latestVol = latestVolOpt.get();

        int windowDays = rule.getWindowDays() != null ? rule.getWindowDays() : 180;
        BigDecimal multiplier = rule.getMultiplier() != null ? rule.getMultiplier() : new BigDecimal("1.8");

        LocalDate from = ts.minusDays(windowDays);

        // Pull vol window
        List<MetricValue> window = metricRepo.findByAssetIdAndMetricTypeAndTsBetweenOrderByTsAsc(
                asset.getId(), MetricType.VOL_20, from, ts
        );

        if (window.size() < 30) return 0; // avoid tiny-history false alerts

        BigDecimal median = median(window.stream().map(MetricValue::getValue).toList());
        BigDecimal threshold = median.multiply(multiplier);

        if (latestVol.getValue().compareTo(threshold) > 0) {
            String msg = "VOL_20 spike: vol=" + latestVol.getValue()
                    + " > median(" + windowDays + "d)*" + multiplier
                    + " (median=" + median + ", threshold=" + threshold + ")";

            return createAlertIfMissing(asset, rule, ts, AlertSeverity.WARN, msg);
        }

        return 0;
    }

    private int evalDrawdown(Asset asset, LocalDate ts, AlertRule rule) {
        int windowDays = rule.getWindowDays() != null ? rule.getWindowDays() : 60;
        BigDecimal threshold = rule.getThreshold() != null ? rule.getThreshold() : new BigDecimal("-0.10");

        LocalDate from = ts.minusDays(windowDays);

        // Pull last window of prices
        List<PriceBar> bars = priceRepo.findByAssetIdAndTsBetweenOrderByTsAsc(asset.getId(), from, ts);
        if (bars.size() < 10) return 0;

        BigDecimal latestClose = bars.get(bars.size() - 1).getClose();
        BigDecimal peak = bars.stream()
                .map(PriceBar::getClose)
                .max(Comparator.naturalOrder())
                .orElse(latestClose);

        if (peak.compareTo(BigDecimal.ZERO) == 0) return 0;

        // drawdown = (latest / peak) - 1
        BigDecimal drawdown = latestClose.divide(peak, 20, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);

        if (drawdown.compareTo(threshold) <= 0) {
            String msg = "Drawdown alert: drawdown=" + drawdown
                    + " <= " + threshold
                    + " (latest=" + latestClose + ", peak(" + windowDays + "d)=" + peak + ")";

            return createAlertIfMissing(asset, rule, ts, AlertSeverity.CRITICAL, msg);
        }

        return 0;
    }

    private int createAlertIfMissing(Asset asset, AlertRule rule, LocalDate ts, AlertSeverity severity, String message) {
        if (eventRepo.existsByAssetIdAndRuleIdAndTs(asset.getId(), rule.getId(), ts)) {
            return 0; // idempotent
        }
        eventRepo.save(new AlertEvent(asset, rule, ts, severity, message));
        return 1;
    }

    private BigDecimal median(List<BigDecimal> xs) {
        List<BigDecimal> sorted = new ArrayList<>(xs);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n == 0) return BigDecimal.ZERO;
        if (n % 2 == 1) return sorted.get(n / 2);
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 20, RoundingMode.HALF_UP);
    }
}
