package com.raj.springmarketanalysis.metric;

import com.raj.springmarketanalysis.config.CacheConfig;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Read-only view of the {@code latestMetrics} cache, for the dashboard: hit/miss
 * counters from the cache writer's statistics collector, plus whether a given
 * asset's entry is currently in Redis and how long it has left.
 * <p>
 * Every Redis lookup here is best-effort — the dashboard must still render when
 * Redis is down, which is the same guarantee the cached endpoints give.
 */
@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheStatsController {

    private final CacheManager cacheManager;
    private final RedisConnectionFactory connectionFactory;

    public CacheStatsController(CacheManager cacheManager, RedisConnectionFactory connectionFactory) {
        this.cacheManager = cacheManager;
        this.connectionFactory = connectionFactory;
    }

    @GetMapping("/latest-metrics/{assetId}")
    public CacheStatus status(@PathVariable Long assetId) {
        String key = CacheConfig.LATEST_METRICS + "::" + assetId;

        long hits = 0;
        long misses = 0;
        long puts = 0;
        boolean statsAvailable = false;

        if (cacheManager.getCache(CacheConfig.LATEST_METRICS) instanceof RedisCache redisCache) {
            try {
                CacheStatistics stats = redisCache.getStatistics();
                hits = stats.getHits();
                misses = stats.getMisses();
                puts = stats.getPuts();
                statsAvailable = true;
            } catch (RuntimeException ignored) {
                // statistics are a nice-to-have; never fail the page over them
            }
        }

        Boolean cached = null;
        Long ttlSeconds = null;
        boolean redisReachable = true;
        try (RedisConnection conn = connectionFactory.getConnection()) {
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            cached = Boolean.TRUE.equals(conn.keyCommands().exists(rawKey));
            if (cached) {
                ttlSeconds = conn.keyCommands().ttl(rawKey);
            }
        } catch (RuntimeException e) {
            redisReachable = false;
        }

        return new CacheStatus(
                CacheConfig.LATEST_METRICS, key, redisReachable, cached, ttlSeconds,
                statsAvailable, hits, misses, puts, hitRate(hits, misses));
    }

    private static Double hitRate(long hits, long misses) {
        long total = hits + misses;
        return total == 0 ? null : (double) hits / total;
    }

    public record CacheStatus(
            String cacheName,
            String key,
            boolean redisReachable,
            Boolean cached,
            Long ttlSeconds,
            boolean statsAvailable,
            long hits,
            long misses,
            long puts,
            Double hitRate
    ) {}
}
