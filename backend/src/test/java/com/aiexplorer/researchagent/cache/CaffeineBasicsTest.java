package com.aiexplorer.researchagent.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T6 第一讲：Caffeine 本地缓存三种用法实测（不启动 Spring，纯 API）。
 *
 * <p>对照讲义：docs/技术栈补齐实战/T6·Caffeine本地缓存.md</p>
 * <p>运行：cd backend && mvn test -Dtest=CaffeineBasicsTest</p>
 */
class CaffeineBasicsTest {

    /** 用法 1：手动 Cache——put/get 全手工 + 容量淘汰 + 统计。 */
    @Test
    @DisplayName("用法1：手动 Cache + 容量淘汰 + 统计")
    void manualCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(2)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        assertThat(cache.getIfPresent("k1")).isEqualTo("v1");

        cache.put("k3", "v3");                      // 容量 2，放第 3 个 → 淘汰一个
        cache.cleanUp();                            // ★ Caffeine 淘汰是异步维护的——断言前强制同步

        var stats = cache.stats();
        System.out.println("[用法1] 条目=" + cache.estimatedSize()
                + " 淘汰数=" + stats.evictionCount()
                + " 命中率=" + String.format("%.2f", stats.hitRate()));

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
        assertThat(stats.evictionCount()).isGreaterThanOrEqualTo(1);
    }

    /** 用法 2：LoadingCache——get 未命中自动调 loader 回填（同 key 只加载一次）。 */
    @Test
    @DisplayName("用法2：LoadingCache 自动加载（loader 只跑一次）")
    void loadingCache() {
        AtomicInteger loadCount = new AtomicInteger();

        LoadingCache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build(key -> {
                    loadCount.incrementAndGet();        // 模拟查库
                    return "db-value-of-" + key;
                });

        String first = cache.get("report-1");           // 未命中 → loader
        String second = cache.get("report-1");          // 命中 → 不走 loader

        System.out.println("[用法2] first=" + first
                + " second=" + second
                + " loader执行=" + loadCount.get()
                + " loadCount(统计)=" + cache.stats().loadCount()
                + " 命中率=" + String.format("%.2f", cache.stats().hitRate()));

        assertThat(first).isEqualTo("db-value-of-report-1");
        assertThat(second).isEqualTo(first);
        assertThat(loadCount.get()).isEqualTo(1);           // ★ 只加载一次
        assertThat(cache.stats().hitRate()).isEqualTo(0.5);  // 2 次 get：1 miss 1 hit
    }

    /**
     * 用法 3：W-TinyLFU 抗"扫描污染"实测。
     *
     * <p>场景：10 个热 key 被反复访问后，来一波 200 个一次性冷 key（模拟全表扫描）。</p>
     * <p>纯 LRU 的结果：热 key 会被扫描流全部冲掉（存活 0~2/10）。</p>
     * <p>Caffeine（W-TinyLFU）：热 key 有频率记录，扫描流进不了主区域 → 热 key 全部存活。</p>
     */
    /**
     * 用法 3：扫描污染下热数据的命运（两个可确定场景对比）。
     *
     * <p>实测结论（Caffeine 3.x，容量 1000）：</p>
     * <ul>
     *   <li><b>扫描量 + 热数据量 &lt; 容量</b>：无淘汰压力 → 热数据 100% 存活（确定性事实）；</li>
     *   <li><b>极端扫描</b>（扫描量数倍于容量）：即使 TinyLFU 有频率保护，
     *       热数据仍会明显被淘汰（本机实测 50 个热 key 只剩个位数）——
     *       <b>本地缓存不是银弹，极端扫描压力要靠 L2（Redis）兜</b>。</li>
     * </ul>
     *
     * <p>★ 顺带踩到的 API 坑：{@code Cache.get(key, fn)} 内部是 computeIfAbsent——
     * 已存在的 key <b>不算读、不累积 TinyLFU 频率</b>；要模拟真实读必须用
     * {@code getIfPresent}（或 {@code get(key, Callable)} 的 LoadingCache）。</p>
     */
    @Test
    @DisplayName("用法3：扫描污染下热数据的命运（容量内全存活 / 极端扫描会被淘汰）")
    void scanPollutionBehavior() {
        final int capacity = 1000;
        final int hotKeyCount = 50;

        // ---- 场景 A：扫描量（500）+ 热数据（50）< 容量（1000）→ 无淘汰压力 ----
        int survivedNormal = runScanScenario(capacity, hotKeyCount, 500, false);
        // ---- 场景 B：极端扫描（3000，3 倍容量）→ 观察热数据存活 ----
        int survivedExtreme = runScanScenario(capacity, hotKeyCount, 3000, true);

        System.out.println("[用法3] 场景A（扫描500 < 容量1000）热存活 = "
                + survivedNormal + "/" + hotKeyCount
                + " ；场景B（扫描3000 极端）热存活 = " + survivedExtreme + "/" + hotKeyCount);

        // 确定性断言：容量内扫描 → 热数据全部存活
        assertThat(survivedNormal).isEqualTo(hotKeyCount);
        // 极端扫描 → 有明显淘汰（不追求具体数值，策略细节依版本；只要"少了"即证明压力）
        assertThat(survivedExtreme).isLessThan(hotKeyCount);
    }

    /** 跑一轮"预热热 key → 扫描冷 key"场景，返回热 key 存活数。 */
    private int runScanScenario(int capacity, int hotKeyCount, int coldCount, boolean keepHotWarm) {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(capacity)
                .recordStats()
                .build();

        for (int hot = 0; hot < hotKeyCount; hot++) {
            cache.put("hot-" + hot, "hot-value");
        }
        for (int round = 0; round < 100; round++) {
            for (int hot = 0; hot < hotKeyCount; hot++) {
                cache.getIfPresent("hot-" + hot);           // 真读，累积频率
            }
        }

        for (int cold = 0; cold < coldCount; cold++) {
            cache.get("cold-" + cold, k -> "cold-value");
            if (keepHotWarm && cold % 10 == 0) {
                cache.getIfPresent("hot-" + (cold % hotKeyCount));
            }
        }
        cache.cleanUp();                                    // 同步维护，统计才准

        int survived = 0;
        for (int hot = 0; hot < hotKeyCount; hot++) {
            if (cache.getIfPresent("hot-" + hot) != null) {
                survived++;
            }
        }
        return survived;
    }
}
