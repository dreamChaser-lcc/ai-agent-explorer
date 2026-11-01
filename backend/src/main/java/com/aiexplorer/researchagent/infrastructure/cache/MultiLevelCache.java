package com.aiexplorer.researchagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * T6 第二讲：两层缓存（L1 Caffeine + L2/DB loader）的教学实现。
 *
 * 读路径：L1 命中直接返回；未命中走 loader（生产中 loader 内部先查 Redis 再查 DB），
 * 结果回填 L1。写路径：更新 L1 + L2。
 *
 * 多实例脏读（本类用 invalidateLocal 显式暴露问题）：
 * 每个进程的 L1 各自独立——实例 A 失效缓存后，实例 B 的 L1 里仍是旧值，
 * 在其 TTL 内一直读到脏数据。解法见讲义：短 TTL / 广播失效 / 版本号。
 *
 * L2 通过 Function/Consumer 抽象注入（生产接 RedisTemplate，测试用内存 Map），零 Redis 依赖。
 */
public class MultiLevelCache<K, V> {

    private final Cache<K, V> l1;
    private final Function<K, V> loader;                      // L2/DB 加载器
    private final Consumer<K> l2Evict;                        // L2 失效回调（可空）
    private final java.util.function.BiConsumer<K, V> l2Put;  // L2 回填回调（可空）

    public MultiLevelCache(Function<K, V> loader, Consumer<K> l2Evict,
            java.util.function.BiConsumer<K, V> l2Put) {
        this.l1 = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofSeconds(30))     // L1 短 TTL：脏读窗口上限
                .recordStats()
                .build();
        this.loader = loader;
        this.l2Evict = l2Evict;
        this.l2Put = l2Put;
    }

    /** 读：L1 → loader(L2/DB) → 回填 L1。 */
    public V get(K key) {
        V hit = l1.getIfPresent(key);
        if (hit != null) {
            return hit;                                       // L1 命中（~100ns）
        }
        V value = loader.apply(key);                          // L2/DB（~1ms / ~10ms）
        if (value != null) {
            l1.put(key, value);                               // 回填 L1
        }
        return value;
    }

    /** 写：双写 L1 + L2（本实例视角）。 */
    public void put(K key, V value) {
        l1.put(key, value);
        if (l2Put != null) {
            l2Put.accept(key, value);
        }
    }

    /** 失效：清 L1 + L2（本实例）。其他实例的 L1 仍是旧值——脏读，需广播。 */
    public void evict(K key) {
        l1.invalidate(key);
        if (l2Evict != null) {
            l2Evict.accept(key);
        }
    }

    /** 广播失效的接收端：其他实例收到消息后调用（只清自己的 L1）。 */
    public void invalidateLocal(K key) {
        l1.invalidate(key);
    }

    /** 观测用：L1 统计（命中率/条目数）。 */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return l1.stats();
    }
}