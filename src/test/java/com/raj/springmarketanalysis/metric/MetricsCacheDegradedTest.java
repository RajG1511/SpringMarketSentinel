package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Redis unreachable: the cache must degrade to a straight database read rather than
 * failing the request. Port 6399 has nothing listening, so every cache operation throws
 * underneath and only {@code CacheConfig}'s error handler keeps the call alive.
 */
@SpringBootTest(properties = "spring.data.redis.port=6399")
class MetricsCacheDegradedTest {

    @Autowired
    MetricsService metricsService;

    @MockitoBean
    AssetRepository assetRepo;
    @MockitoBean
    MetricValueRepository metricRepo;
    @MockitoBean
    PriceBarRepository priceRepo;

    private final Asset asset = new Asset("SPY", "SPDR S&P 500", "ETF");

    @Test
    void readsFallThroughToTheDatabase() {
        when(assetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(1L, MetricType.SMA_20))
                .thenReturn(Optional.of(new MetricValue(
                        asset, LocalDate.of(2026, 7, 24), MetricType.SMA_20, new BigDecimal("123.45"))));

        MetricsService.LatestMetrics result = metricsService.getLatestMetrics(1L);

        assertThat(result.sma20()).isEqualByComparingTo("123.45");
        assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    void evictionFailureDoesNotBreakRecompute() {
        when(assetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(priceRepo.findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(any(), any()))
                .thenReturn(List.of());

        assertThatCode(() -> metricsService.computeMetricsForAsset(1L, 400))
                .doesNotThrowAnyException();
    }
}
