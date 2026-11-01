package com.aiexplorer.researchagent.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T6 第二讲：两层缓存的读路径/写路径/多实例脏读演示（对照 docs/技术栈补齐实战/T6 讲义）。 */
class MultiLevelCacheTest {

    @Test
    @DisplayName("读路径：L1 未命中走 loader，回填后第二次不再加载")
    void loadOnce() {
        AtomicInteger dbCalls = new AtomicInteger();
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                key -> { dbCalls.incrementAndGet(); return "db:" + key; },
                null, null);

        String first = cache.get("r1");
        String second = cache.get("r1");

        assertThat(first).isEqualTo("db:r1");
        assertThat(second).isEqualTo(first);
        assertThat(dbCalls.get()).isEqualTo(1);              // loader 只执行一次
        assertThat(cache.stats().hitRate()).isEqualTo(0.5);  // 2 次 get：1 miss 1 hit
    }

    @Test
    @DisplayName("写路径：put 双写 L1+L2；evict 清两层")
    void putAndEvict() {
        Map<String, String> l2 = new HashMap<>();            // 模拟 Redis
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                key -> l2.get(key),
                key -> l2.remove(key),
                (key, value) -> l2.put(key, value));

        cache.put("r1", "v1");
        assertThat(l2).containsEntry("r1", "v1");            // L2 已写
        assertThat(cache.get("r1")).isEqualTo("v1");         // L1 命中

        cache.evict("r1");
        assertThat(l2).doesNotContainKey("r1");              // L2 已失效
        assertThat(cache.get("r1")).isNull();                // L1 空 → loader 查 L2 → null
    }

    @Test
    @DisplayName("多实例脏读：A 失效后 B 的 L1 仍是旧值；广播失效后恢复一致")
    void multiInstanceStaleRead() {
        Map<String, String> l2 = new HashMap<>();            // 共享 L2（模拟 Redis）
        l2.put("r1", "v1");

        // 两个实例：各自的 L1（Caffeine），共享 L2 —— 这就是多实例部署
        MultiLevelCache<String, String> instanceA = new MultiLevelCache<>(
                key -> l2.get(key), key -> l2.remove(key), (k, v) -> l2.put(k, v));
        MultiLevelCache<String, String> instanceB = new MultiLevelCache<>(
                key -> l2.get(key), key -> l2.remove(key), (k, v) -> l2.put(k, v));

        // 都读一次 → 各自 L1 都缓存了 v1
        assertThat(instanceA.get("r1")).isEqualTo("v1");
        assertThat(instanceB.get("r1")).isEqualTo("v1");

        // 数据变更：A 更新（DB 已改 → A 回填 L2 为 v2 + 刷自己的 L1）
        instanceA.put("r1", "v2");
        assertThat(instanceA.get("r1")).isEqualTo("v2");     // A 读到新值

        // ★ 脏读：B 的 L1 还是 v1（在它 30s TTL 内一直脏）
        assertThat(instanceB.get("r1")).isEqualTo("v1");

        // 广播失效：A 发布消息，B 收到后 invalidateLocal（清自己的 L1）→ 恢复一致
        instanceB.invalidateLocal("r1");
        assertThat(instanceB.get("r1")).isEqualTo("v2");     // ← 新值 ✓
    }
}