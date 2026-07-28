package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.price.PriceBar;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for the metric math. Uses a constant price series so every
 * expected value is exact (returns 0, SMA equal to the price, volatility 0).
 */
class MetricsServiceTest {

    private final AssetRepository assetRepo = mock(AssetRepository.class);
    private final PriceBarRepository priceRepo = mock(PriceBarRepository.class);
    private final MetricValueRepository metricRepo = mock(MetricValueRepository.class);

    private final MetricsService service = new MetricsService(assetRepo, priceRepo, metricRepo);

    private final Asset asset = new Asset("SPY", "SPDR S&P 500", "ETF");

    private List<MetricValue> compute(List<PriceBar> bars) {
        when(assetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(priceRepo.findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(any(), any())).thenReturn(bars);
        when(metricRepo.existsByAssetIdAndTsAndMetricType(any(), any(), any())).thenReturn(false);
        when(metricRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.computeMetricsForAsset(1L, 400);

        ArgumentCaptor<MetricValue> captor = ArgumentCaptor.forClass(MetricValue.class);
        verify(metricRepo, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<MetricValue> ofType(List<MetricValue> all, MetricType type) {
        return all.stream().filter(m -> m.getMetricType() == type).toList();
    }

    @Test
    void constantPriceSeriesYieldsZeroReturnsFlatSmaAndZeroVol() {
        List<PriceBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 25; i++) {
            bars.add(bar(start.plusDays(i), "100"));
        }

        List<MetricValue> saved = compute(bars);

        // 24 daily returns, all exactly zero
        List<MetricValue> returns = ofType(saved, MetricType.RETURN_1D);
        assertThat(returns).hasSize(24);
        assertThat(returns).allSatisfy(m -> assertThat(m.getValue()).isEqualByComparingTo("0"));

        // SMA_20 computable for the last 6 of 25 bars, each equal to the price
        List<MetricValue> sma20 = ofType(saved, MetricType.SMA_20);
        assertThat(sma20).hasSize(6);
        assertThat(sma20).allSatisfy(m -> assertThat(m.getValue()).isEqualByComparingTo("100"));

        // VOL_20 computable for the last 5 bars, zero because returns are constant
        List<MetricValue> vol20 = ofType(saved, MetricType.VOL_20);
        assertThat(vol20).hasSize(5);
        assertThat(vol20).allSatisfy(m -> assertThat(m.getValue()).isEqualByComparingTo("0"));

        // Not enough history for a 50-day average
        assertThat(ofType(saved, MetricType.SMA_50)).isEmpty();
    }

    @Test
    void dailyReturnIsCurrentOverPreviousMinusOne() {
        List<PriceBar> bars = List.of(
                bar(LocalDate.of(2026, 1, 1), "100"),
                bar(LocalDate.of(2026, 1, 2), "110"),
                bar(LocalDate.of(2026, 1, 3), "121"));

        List<MetricValue> returns = ofType(compute(bars), MetricType.RETURN_1D);

        assertThat(returns).hasSize(2);
        assertThat(returns.get(0).getValue()).isEqualByComparingTo("0.10"); // 110/100 - 1
        assertThat(returns.get(1).getValue()).isEqualByComparingTo("0.10"); // 121/110 - 1
    }

    private PriceBar bar(LocalDate ts, String close) {
        BigDecimal c = new BigDecimal(close);
        return new PriceBar(asset, ts, c, c, c, c, 1_000_000L);
    }
}
