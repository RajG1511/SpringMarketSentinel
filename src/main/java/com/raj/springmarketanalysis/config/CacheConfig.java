package com.raj.springmarketanalysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.raj.springmarketanalysis.metric.MetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Enables Spring's caching and backs it with Redis. Cache values are stored as
 * JSON (not JDK-serialized) so DTO records round-trip and are human-readable in
 * Redis. Redis connections are lazy, so the app still boots when Redis is down —
 * cached reads just fall through to the database until it's reachable.
 * <p>
 * Each cache is registered explicitly with a serializer bound to its value type,
 * so adding a cache means adding a {@code withCacheConfiguration} entry here.
 * That is intentional: it keeps cached JSON free of embedded type information,
 * which would otherwise let whoever can write to Redis pick the class the app
 * instantiates while reading the cache.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String LATEST_METRICS = "latestMetrics";

    /**
     * Spring's default error handler rethrows, so an unreachable Redis turns a cached
     * read into a 500 even though the database could have answered it. Logging instead
     * of rethrowing makes the cache strictly an optimisation: reads fall through to the
     * database and writes are dropped while Redis is down.
     * <p>
     * The trade-off is on eviction — a dropped evict leaves a stale entry that only the
     * TTL will clear, so a recompute during a Redis outage may not be visible for up to
     * {@code market.cache.ttlSeconds}.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${market.cache.ttlSeconds:300}") long ttlSeconds
    ) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(cacheObjectMapper(), MetricsService.LatestMetrics.class)));

        return RedisCacheManager.builder(cacheWriter(connectionFactory))
                .withCacheConfiguration(LATEST_METRICS, config)
                .disableCreateOnMissingCache() // an unregistered cache name fails fast instead of falling back to JDK serialization
                .enableStatistics()            // hit/miss counters, surfaced by CacheStatsController
                .build();
    }

    /**
     * With a driver that supports asynchronous retrieval (Lettuce), Spring Data Redis 4.0
     * makes cache writes fire-and-forget: {@code put} returns before Redis has applied the
     * SET, so a read immediately after a miss can miss again, and write failures are
     * swallowed. {@code immediateWrites()} restores synchronous puts, which is what the
     * read-through behaviour here assumes — the SET is local and sub-millisecond.
     */
    private RedisCacheWriter cacheWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.create(connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);
    }

    /**
     * Jackson mapper for cache values: Java-time support so {@code LocalDate}
     * round-trips as an ISO string.
     * <p>
     * Deliberately no polymorphic default typing. The target type is fixed by the
     * serializer above, so nothing in a cached payload can choose which class gets
     * instantiated on read — a value forged by anyone able to write to Redis can
     * only ever deserialize as {@code LatestMetrics}.
     */
    private ObjectMapper cacheObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
