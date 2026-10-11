package com.aiexplorer.researchagent.infrastructure.config;

import com.aiexplorer.researchagent.api.response.ResearchReportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * 缓存配置（场景⑦）——自定义 Redis 缓存的"序列化方式"与 TTL。
 *
 * <p>★ 为什么必须自定义（出厂默认的坑）：Spring Cache + Redis 默认用
 * {@code JdkSerializationRedisSerializer}（JDK 序列化）——它要求缓存对象实现
 * {@code java.io.Serializable}；而我们缓存的值是 record（ResearchReportResponse）——
 * 直接抛 "Cannot serialize … requires a Serializable payload"
 * （每个团队第一次接 Redis 缓存几乎都会撞它一次）。</p>
 *
 * <p>方案对比（为什么选 JSON，而不是"给 record 加 Serializable"）：
 * <ul>
 *   <li>JDK 序列化：二进制存进 Redis（redis-cli 里不可读）+ 类结构一变旧缓存就反序列化失败——脆弱；</li>
 *   <li>★ JSON 序列化（本配置）：人类可读（redis-cli 直接看内容）、跨语言、跨版本友好——主流选择。</li>
 * </ul></p>
 *
 * <p>两个细节：
 * ① 指定目标类型 {@code ResearchReportResponse.class}——读回来时直接构造该类型，
 *    不依赖 JSON 内嵌类型信息（避开"final 类不嵌类型 → 读回变 Map"的坑）；
 * ② 注册 {@code JavaTimeModule}——否则 OffsetDateTime 等 Java 8 时间类型没有序列化支持（会二次爆炸）。</p>
 */
@Configuration
public class CacheConfiguration {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        // 专为"缓存值"准备的 ObjectMapper：支持 Java 8 时间 + 时间存 ISO 文本（可读）
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<ResearchReportResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, ResearchReportResponse.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                // ★ TTL（30 分钟）——由本配置接管（yml 里的 time-to-live 不再生效——见 application.yml 注释）
                .entryTtl(Duration.ofMinutes(30))
                // 值：JSON 序列化；key 保持默认的 String 序列化（redis-cli 里 key 形如 report::<uuid>，直观）
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }
}
