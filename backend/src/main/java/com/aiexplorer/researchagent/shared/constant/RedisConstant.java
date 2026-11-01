package com.aiexplorer.researchagent.shared.constant;

import java.util.UUID;

/**
 * Redis Key 统一管理（T5 规范落地：Redis Key 常量化）。
 *
 * <p>改造前散落三处：</p>
 * <pre>
 * · DistributedTaskLock 类内私有 KEY_PREFIX = "lock:task:exec:"
 * · RetentionCleanupJob: lock.tryLock("cleanup")        ← 裸字符串
 * · @Cacheable(value = "report") / @CacheEvict(value = "report")  ← 裸字符串 ×2
 *
 * 问题：拼写错误编译期查不出；前缀风格不统一；无法一处看全"项目用了哪些 Redis key"。
 * </pre>
 *
 * <p><b>命名规范</b>（本类遵守）：</p>
 * <ul>
 *   <li>全部小写，冒号分层：{@code 类型:业务域:实体:标识}</li>
 *   <li>{@code lock:} 前缀 = 分布式锁；{@code cache:} 前缀 = 缓存（新项目建议）</li>
 *   <li>动态部分（id）由静态构造方法拼接，禁止调用方手拼字符串</li>
 * </ul>
 *
 * <p><b>迁移策略</b>（与 T1/T2 一致的渐进式）：</p>
 * <ul>
 *   <li>值保持与原字符串一致（{@code CACHE_REPORT = "report"}）——常量化不改运行时行为，
 *       避免已有缓存全部失效；</li>
 *   <li>将来新写缓存/锁优先使用本类；旧代码迭代时顺手替换。</li>
 * </ul>
 */
public final class RedisConstant {

    /** 工具类禁止实例化。 */
    private RedisConstant() {
    }

    /* ==================== 分布式锁（lock:） ==================== */

    /** 任务执行锁前缀（场景⑨）：完整 key = 前缀 + taskId。 */
    public static final String LOCK_TASK_EXEC_PREFIX = "lock:task:exec:";

    /** 定时清理任务的全局锁（场景⑩）——单实例执行，全集群共用一把。 */
    public static final String LOCK_CLEANUP = "lock:cleanup";

    /**
     * 构造任务执行锁的完整 key。
     *
     * @param taskId 任务主键
     * @return {@code lock:task:exec:<taskId>}
     */
    public static String lockTaskExec(UUID taskId) {
        return LOCK_TASK_EXEC_PREFIX + taskId;
    }

    /* ==================== 缓存（Spring Cache 逻辑名） ==================== */

    /**
     * 研究报告缓存（场景⑦）。
     *
     * <p>注意：值是 Spring Cache 的<b>逻辑 cacheName</b>，不是 Redis 的完整 key——
     * 实际 Redis key 由 cacheName + "::" + 业务 key 组成（如 {@code report::<taskId>}）。
     * 保持 "report" 不变 = 已有缓存不失效。</p>
     */
    public static final String CACHE_REPORT = "report";

    /* ==================== 预留（未来演进示例） ==================== */

    /*
     * 如果将来引入"任务详情缓存"，命名参考：
     *   public static final String CACHE_TASK_DETAIL_PREFIX = "cache:task:detail:";
     *   public static String cacheTaskDetail(UUID taskId) { return CACHE_TASK_DETAIL_PREFIX + taskId; }
     *
     * 演进到多实例/多环境时再加环境前缀（如 prod:/dev:）——由配置注入，不要硬编码。
     */
}
