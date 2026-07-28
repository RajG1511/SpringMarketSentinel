package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.asset.Asset;
import com.raj.springmarketanalysis.asset.AssetRepository;
import com.raj.springmarketanalysis.config.CacheConfig;
import com.raj.springmarketanalysis.price.PriceBarRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the Redis-backed cache end to end (requires Postgres + Redis): repeated
 * reads hit the database once, the value survives JSON (de)serialization, and
 * recomputing metrics evicts the entry.
 */
@SpringBootTest(properties = "spring.data.redis.database=1") // isolate from the app's Redis DB 0
class MetricsCacheIntegrationTest {

    @Autowired
    MetricsService metricsService;

    @Autowired
    CacheManager cacheManager;

    @MockitoBean
    AssetRepository assetRepo;

    @MockitoBean
    MetricValueRepository metricRepo;

    @MockitoBean
    PriceBarRepository priceRepo;

    private final Asset asset = new Asset("SPY", "SPDR S&P 500", "ETF");

    @AfterEach
    void tearDown() {
        // Don't leave entries in the shared dev Redis instance.
        cacheManager.getCache(CacheConfig.LATEST_METRICS).clear();
    }

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.LATEST_METRICS).clear();
        when(assetRepo.findById(1L)).thenReturn(Optional.of(asset));
        when(metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(metricRepo.findTopByAssetIdAndMetricTypeOrderByTsDesc(1L, MetricType.SMA_20))
                .thenReturn(Optional.of(new MetricValue(
                        asset, LocalDate.of(2026, 7, 24), MetricType.SMA_20, new BigDecimal("123.45"))));
    }

    @Test
    void secondReadIsServedFromCache() {
        MetricsService.LatestMetrics first = metricsService.getLatestMetrics(1L);
        MetricsService.LatestMetrics second = metricsService.getLatestMetrics(1L);

        // Value survived JSON round-trip through Redis
        assertThat(second.sma20()).isEqualByComparingTo("123.45");
        assertThat(second.asOf()).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(second).isEqualTo(first);

        // 4 metric types queried once total; the second read never touched the DB
        verify(metricRepo, times(4)).findTopByAssetIdAndMetricTypeOrderByTsDesc(any(), any());
    }

    /**
     * Guards {@code CacheConfig}'s use of {@code immediateWrites()}. Spring Data Redis 4.0
     * defaults to fire-and-forget writes with Lettuce, which makes a put visible only some
     * milliseconds later — with that default this loop misses on roughly half the iterations
     * and {@link #secondReadIsServedFromCache()} becomes flaky rather than failing outright.
     */
    @Test
    void cacheWritesAreVisibleImmediately() {
        Cache cache = cacheManager.getCache(CacheConfig.LATEST_METRICS);
        MetricsService.LatestMetrics value = new MetricsService.LatestMetrics(
                1L, "SPY", LocalDate.of(2026, 7, 24), null, new BigDecimal("123.45"), null, null);

        for (int i = 0; i < 25; i++) {
            String key = "immediate-" + i;
            cache.put(key, value);
            assertThat(cache.get(key))
                    .as("cache entry %s must be readable straight after put", key)
                    .isNotNull();
        }
    }

    @Test
    void recomputeEvictsTheCachedEntry() {
        when(priceRepo.findByAssetIdAndTsGreaterThanEqualOrderByTsAsc(any(), any()))
                .thenReturn(List.of()); // not enough data: returns early, but still evicts

        metricsService.getLatestMetrics(1L);          // populates cache (4 queries)
        metricsService.computeMetricsForAsset(1L, 400); // @CacheEvict
        metricsService.getLatestMetrics(1L);          // cache miss -> 4 more queries

        verify(metricRepo, times(8)).findTopByAssetIdAndMetricTypeOrderByTsDesc(any(), any());
    }
}
