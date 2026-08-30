package com.aiexplorer.researchagent.infrastructure.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 缓存配置。
 *
 * 职责：
 *   1. 开启 Spring Cache 注解能力（@Cacheable / @CacheEvict / @CachePut）
 *   2. 使用 Redis 作为缓存后端，并统一缓存的序列化方式与默认过期时间
 *
 * 关键点：
 *   - 默认使用 JDK 序列化时，缓存对象必须实现 Serializable，且 key 会带 \xAC\xED 乱码。
 *     这里改用 JSON 序列化（GenericJackson2JsonRedisSerializer），
 *     缓存内容可读、无需改造实体类，也方便与其它语言/工具互通。
 *   - key 使用 String 序列化，保证 key 干净可读（形如 task:detail::<UUID>）。
 *
 * 命名说明：类名避免与 Spring 自带的 RedisCacheConfiguration 冲突，故命名为 RedisCacheConfig。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * 构建基于 Redis 的缓存管理器，供 @Cacheable 等注解使用。
     *
     * @param connectionFactory Spring Boot 自动配置的 Redis 连接工厂
     * @return 配置好的 RedisCacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存策略：JSON 序列化 value + String 序列化 key + 默认过期时间
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30)) // 任务详情状态变化较快，短 TTL 避免读到过旧数据
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // 不缓存 null，避免缓存穿透放大

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}
