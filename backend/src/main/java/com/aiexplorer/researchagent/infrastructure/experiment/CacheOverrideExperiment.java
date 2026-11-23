package com.aiexplorer.researchagent.infrastructure.experiment;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SB1 实验 1：@ConditionalOnMissingBean 的"正常覆盖"验证。
 *
 * <p>启动：{@code --spring.profiles.active=lite,cache-experiment --debug}</p>
 *
 * <p><b>预期</b>：</p>
 * <pre>
 *   ① --debug 的 report 里 SimpleCacheConfiguration 由 Positive 变【Negative】
 *      （原因：CacheAutoConfiguration 的默认 CacheManager 是 @ConditionalOnMissingBean(CacheManager)，
 *        我们定义了 experimentCacheManager → 条件不成立 → 默认实现让位）
 *   ② /actuator/beans 里 cacheManager 的 type = ConcurrentMapCacheManager（我们定义的）
 *   ③ 应用正常启动（@Cacheable 用我们的 CacheManager）
 * </pre>
 *
 * <p><b>为什么能证明"顺序"</b>：用户配置类（@ComponentScan 扫描）先于自动配置类解析，
 * 所以自动配置的 ConditionEvaluator 判断时"已经能看到"我们的 Bean。</p>
 */
@Configuration
@Profile("cache-experiment")
public class CacheOverrideExperiment {

    /** 故意用 bean 名 cacheManager（与 Boot 默认同名同类型），验证"用户定义 → 默认让位"。 */
    @Bean
    public CacheManager cacheManager() {
        System.out.println("[实验1] 自定义 CacheManager 装配（Boot 的默认 CacheManager 应让位）");
        return new ConcurrentMapCacheManager("experiment-cache");
    }
}
