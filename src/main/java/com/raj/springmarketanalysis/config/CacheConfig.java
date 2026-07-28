package com.raj.springmarketanalysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Enables Spring's caching and backs it with Redis. Cache values are stored as
 * JSON (not JDK-serialized) so DTO records round-trip and are human-readable in
 * Redis. Redis connections are lazy, so the app still boots when Redis is down —
 * cached reads just fall through to the database until it's reachable.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LATEST_METRICS = "latestMetrics";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${market.cache.ttlSeconds:300}") long ttlSeconds
    ) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));

        return RedisCacheManager.builder(cacheWriter(connectionFactory))
                .cacheDefaults(config)
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
     * Jackson mapper for cache values: Java-time support for LocalDate, and
     * default typing (including final record types) so values deserialize back
     * to their concrete class rather than a generic map.
     */
    private ObjectMapper cacheObjectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING)
                .build();
    }
}
