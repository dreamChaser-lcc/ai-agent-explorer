package com.aiexplorer.researchagent.infrastructure.experiment;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SB1 实验 2（陷阱）：@ConditionalOnMissingBean 只解决"一个覆盖默认"，不解决"你的多个打架"。
 *
 * <p>启动：{@code --spring.profiles.active=lite,cache-conflict}</p>
 *
 * <p><b>预期</b>：启动失败 —— 容器里有两个 CacheManager Bean，注入点（@Cacheable 基础设施 /
 * CacheAspectSupport）要求唯一 → NoUniqueBeanDefinitionException（或 cacheManager 名称冲突）。</p>
 *
 * <p><b>结论</b>：@ConditionalOnMissingBean 的语义是"没有才给"——它退让的前提是"你恰好给了一个"；
 * 给两个 = 它退让了，但你的两个没人裁决（需要 @Primary 或 @Qualifier 指定）。</p>
 */
@Configuration
@Profile("cache-conflict")
public class CacheConflictExperiment {

    @Bean
    public CacheManager cacheManagerA() {
        System.out.println("[实验2] cacheManagerA 装配");
        return new ConcurrentMapCacheManager("cache-a");
    }

    @Bean
    public CacheManager cacheManagerB() {
        System.out.println("[实验2] cacheManagerB 装配");
        return new ConcurrentMapCacheManager("cache-b");
    }
}
