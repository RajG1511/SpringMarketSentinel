package com.raj.springmarketanalysis.alert;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.metric.MetricType;
import com.raj.springmarketanalysis.metric.MetricValue;
import com.raj.springmarketanalysis.metric.MetricValueRepository;
import com.raj.springmarketanalysis.notification.AlertNotification;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for the alert rule logic: no Spring context, no database.
 * Repositories are mocked so we exercise only the decision + firing behaviour.
 */
class AlertsServiceTest {

    private static final LocalDate TS = LocalDate.of(2026, 7, 24);

    private final AssetRepository assetRepo = mock(AssetRepository.class);
    private final AlertRuleRepository ruleRepo = mock(AlertRuleRepository.class);
    private final AlertEventRepository eventRepo = mock(AlertEventRepository.class);
    private final MetricValueRepository metricRepo = mock(MetricValueRepository.class);
    private final PriceBarRepository priceRepo = mock(PriceBarRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final AlertsService service =
            new AlertsService(assetRepo, ruleRepo, eventRepo, metricRepo, priceRepo, events);

    private final Asset asset = new Asset("SPY", "SPDR S&P 500", "ETF");

    private void commonStubs(AlertRule rule) {
        when(assetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(priceRepo.findTopByAssetIdOrderByTsDesc(1L))
                .thenReturn(Optional.of(bar(TS, "130")));
        when(ruleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(eventRepo.existsByAssetIdAndRuleIdAndTs(any(), any(), any())).thenReturn(false);
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void drawdownFiresWhenPriceIsMoreThanTenPercentBelowPeak() {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getRuleKey()).thenReturn("DRAWDOWN_10PCT");
        when(rule.getWindowDays()).thenReturn(60);
        when(rule.getThreshold()).thenReturn(new BigDecimal("-0.10"));
        commonStubs(rule);

        // Peak 160, latest 130 -> drawdown = 130/160 - 1 = -18.75%
        List<PriceBar> window = List.of(
                bar(TS.minusDays(11), "100"), bar(TS.minusDays(10), "120"),
                bar(TS.minusDays(9), "140"), bar(TS.minusDays(8), "160"),
                bar(TS.minusDays(7), "155"), bar(TS.minusDays(6), "150"),
                bar(TS.minusDays(5), "145"), bar(TS.minusDays(4), "140"),
                bar(TS.minusDays(3), "138"), bar(TS.minusDays(2), "135"),
                bar(TS.minusDays(1), "132"), bar(TS, "130"));
        when(priceRepo.findByAssetIdAndTsBetweenOrderByTsAsc(any(), any(), any())).thenReturn(window);

        AlertsService.AlertRunResult result = service.evaluateAlertsForAsset(1L);

        assertThat(result.fired()).isEqualTo(1);
        verify(eventRepo).save(any(AlertEvent.class));
        verify(events).publishEvent(any(AlertNotification.class));
    }

    @Test
    void drawdownDoesNotFireWhenDeclineIsShallow() {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getRuleKey()).thenReturn("DRAWDOWN_10PCT");
        when(rule.getWindowDays()).thenReturn(60);
        when(rule.getThreshold()).thenReturn(new BigDecimal("-0.10"));
        commonStubs(rule);

        // Peak 135, latest 130 -> drawdown ~ -3.7%, above the -10% threshold
        List<PriceBar> window = List.of(
                bar(TS.minusDays(11), "130"), bar(TS.minusDays(10), "131"),
                bar(TS.minusDays(9), "132"), bar(TS.minusDays(8), "135"),
                bar(TS.minusDays(7), "134"), bar(TS.minusDays(6), "133"),
                bar(TS.minusDays(5), "132"), bar(TS.minusDays(4), "131"),
                bar(TS.minusDays(3), "131"), bar(TS.minusDays(2), "130"),
                bar(TS.minusDays(1), "130"), bar(TS, "130"));
        when(priceRepo.findByAssetIdAndTsBetweenOrderByTsAsc(any(), any(), any())).thenReturn(window);

        AlertsService.AlertRunResult result = service.evaluateAlertsForAsset(1L);

        assertThat(result.fired()).isZero();
        verify(eventRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void maCrossoverFiresOnGoldenCross() {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getRuleKey()).thenReturn("MA_CROSSOVER_20_50");
        commonStubs(rule);

        // SMA_20 goes 99 -> 101 while SMA_50 stays 100: golden cross on the latest day.
        when(metricRepo.findTop2ByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
                any(), eq(MetricType.SMA_20), any()))
                .thenReturn(List.of(metric(TS, "101"), metric(TS.minusDays(1), "99")));
        when(metricRepo.findTop2ByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
                any(), eq(MetricType.SMA_50), any()))
                .thenReturn(List.of(metric(TS, "100"), metric(TS.minusDays(1), "100")));

        AlertsService.AlertRunResult result = service.evaluateAlertsForAsset(1L);

        assertThat(result.fired()).isEqualTo(1);
        verify(events).publishEvent(any(AlertNotification.class));
    }

    @Test
    void maCrossoverDoesNotFireWithoutSignChange() {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getRuleKey()).thenReturn("MA_CROSSOVER_20_50");
        commonStubs(rule);

        // SMA_20 stays above SMA_50 on both days: no cross.
        when(metricRepo.findTop2ByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
                any(), eq(MetricType.SMA_20), any()))
                .thenReturn(List.of(metric(TS, "105"), metric(TS.minusDays(1), "104")));
        when(metricRepo.findTop2ByAssetIdAndMetricTypeAndTsLessThanEqualOrderByTsDesc(
                any(), eq(MetricType.SMA_50), any()))
                .thenReturn(List.of(metric(TS, "100"), metric(TS.minusDays(1), "100")));

        AlertsService.AlertRunResult result = service.evaluateAlertsForAsset(1L);

        assertThat(result.fired()).isZero();
        verify(events, never()).publishEvent(any());
    }

    private PriceBar bar(LocalDate ts, String close) {
        BigDecimal c = new BigDecimal(close);
        return new PriceBar(asset, ts, c, c, c, c, 1_000_000L);
    }

    private MetricValue metric(LocalDate ts, String value) {
        return new MetricValue(asset, ts, MetricType.SMA_20, new BigDecimal(value));
    }
}
