package com.aiexplorer.researchagent.infrastructure.cleanup;

import com.aiexplorer.researchagent.application.service.RetentionCleanupService;
import com.aiexplorer.researchagent.infrastructure.lock.DistributedTaskLock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 场景⑩：定时清理任务（@Scheduled 入口）。
 *
 * 三个设计点：
 *   ① fixedDelay 而不是 fixedRate：上一次跑完再等间隔，避免堆积；
 *   ② 分布式锁：多实例部署时只让一个实例真正清理（复用场景⑨ 的 Redis 锁）；
 *   ③ 异常不外抛：定时任务抛异常会被调度器吞掉，这里显式记录后继续。
 */
@Component
public class RetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupJob.class);

    private final RetentionCleanupService cleanupService;
    private final DistributedTaskLock lock;

    public RetentionCleanupJob(RetentionCleanupService cleanupService, DistributedTaskLock lock) {
        this.cleanupService = cleanupService;
        this.lock = lock;
    }

    @Scheduled(
            initialDelayString = "${app.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${app.cleanup.interval-ms:3600000}")
    public void scheduledCleanup() {
        Optional<String> token = lock.tryLock("cleanup");
        if (token.isEmpty()) {
            log.info("[清理] 跳过：另一个实例正在清理");
            return;
        }
        try {
            cleanupService.cleanupOnce();
        } catch (Exception exception) {
            log.error("[清理] 执行失败：{}", exception.getMessage(), exception);
        } finally {
            lock.unlock("cleanup", token.get());
        }
    }
}