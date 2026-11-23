package com.aiexplorer.researchagent.demo;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/**
 * T9·XXL-Job demo handler——在调度中心控制台"任务管理"新建任务时，JobHandler 字段填这里的方法名。
 *
 * <p>前提：application.yml {@code xxl.job.enabled=true} 且调度中心已启动
 * （未启用时 XxlJobSpringExecutor 不创建，这些方法不会被调度执行——无副作用）。</p>
 *
 * <p>三个 demo：</p>
 * <ol>
 *   <li>{@code demoHelloJob}：最小闭环（控制台手动执行一次即可看到日志）</li>
 *   <li>{@code demoShardingJob}：分片广播（路由策略选"分片广播"时生效，观察 index/total）</li>
 *   <li>{@code retentionCleanupJobHandler}：场景⑩ 迁移样板（迁移路线见 T9 文档 §6）</li>
 * </ol>
 *
 * <p>★ 幂等提醒：调度中心可能重试派发/故障转移换实例重跑——handler 内业务仍需幂等
 * （状态守卫/唯一约束，场景④⑥ 的套路照用）。</p>
 */
@Component
public class XxlJobDemoHandler {

    /** 任务 1：hello——控制台"执行一次"即可验证调度链路是否打通。 */
    @XxlJob("demoHelloJob")
    public void demoHelloJob() {
        String jobParam = XxlJobHelper.getJobParam();
        XxlJobHelper.log("hello from XXL-Job! 执行参数：{}", jobParam);
        XxlJobHelper.handleSuccess();
    }

    /** 任务 2：分片广播——路由策略选"分片广播"时，每个实例拿到自己的 index。 */
    @XxlJob("demoShardingJob")
    public void demoShardingJob() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        // 分片并行示例：按 id % shardTotal == shardIndex 拆分数据给各实例
        XxlJobHelper.log("分片执行：index={}/total={}", shardIndex, shardTotal);
        XxlJobHelper.handleSuccess();
    }

    /** 任务 3：场景⑩ 迁移样板——业务逻辑调用与 RetentionCleanupJob 完全一致。 */
    @XxlJob("retentionCleanupJobHandler")
    public void retentionCleanupJobHandler() {
        // 迁移路线（T9 文档 §6）：双轨期先并存 → 切流观察 → 删 @Scheduled 入口与 Redis 锁
        // （调度互斥由 XXL-Job 平台保证，不再需要 DistributedTaskLock）；
        // cleanupOnce() 内部的幂等逻辑保留（调度重试仍可能重复派发）。
        XxlJobHelper.log("场景⑩ 清理逻辑迁移样板：此处应调用 RetentionCleanupService.cleanupOnce()");
        XxlJobHelper.handleSuccess();
    }
}
