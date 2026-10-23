package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.cleanup.CleanupProperties;
import com.aiexplorer.researchagent.infrastructure.cleanup.CleanupResult;
import com.aiexplorer.researchagent.infrastructure.cleanup.RetentionCleanupDao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景⑩：数据保留清理（过期任务 + 旧事件 + 旧审计）。
 *
 * 清理顺序（先子后父）：
 *   step_execution / research_step / source_document / research_plan
 *   / research_report / human_confirmation / task_event_log → research_task
 *
 * 三个安全约束：
 *   ① 只删【终态】任务（COMPLETED / CANCELLED / FAILED）——运行中的任务绝不碰；
 *   ② 分批（batchSize）——避免一次删太多造成长事务/锁表；
 *   ③ 幂等——重复执行结果一致（按时间条件删，删过就没了）。
 */
@Service
public class RetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupService.class);

    /** 只有这些终态的任务才允许被清理。 */
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "CANCELLED", "FAILED");

    private final RetentionCleanupDao cleanupDao;
    private final CleanupProperties properties;

    public RetentionCleanupService(RetentionCleanupDao cleanupDao, CleanupProperties properties) {
        this.cleanupDao = cleanupDao;
        this.properties = properties;
    }

    /**
     * 执行一次清理。
     *
     * @return 统计结果（各表删除行数与耗时）
     */
    @Transactional
    public CleanupResult cleanupOnce() {
        if (!properties.isEnabled()) {
            log.info("[清理] 已禁用（app.cleanup.enabled=false），跳过");
            return CleanupResult.skipped();
        }
        long startMilliseconds = System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();

        // ① 过期任务（终态 + 超过保留期）
        OffsetDateTime taskCutoff = now.minusDays(properties.getTaskRetentionDays());
        List<UUID> expiredTaskIds = cleanupDao.findExpiredTaskIds(
                TERMINAL_STATUSES, taskCutoff, properties.getBatchSize());
        int childRows = 0;
        int taskRows = 0;
        if (!expiredTaskIds.isEmpty()) {
            childRows = cleanupDao.deleteChildrenByTaskIds(expiredTaskIds, properties.getBatchSize());
            taskRows = cleanupDao.deleteTasksByIds(expiredTaskIds, properties.getBatchSize());
        }

        // ② 旧事件（保留期外）
        int orphanEvents = cleanupDao.deleteOldEvents(
                now.minusDays(properties.getEventRetentionDays()), properties.getBatchSize());

        // ③ 旧审计
        int auditLogs = cleanupDao.deleteOldAuditLogs(
                now.minusDays(properties.getAuditRetentionDays()), properties.getBatchSize());

        long elapsedMilliseconds = System.currentTimeMillis() - startMilliseconds;
        CleanupResult result = new CleanupResult(
                taskRows, childRows, orphanEvents, auditLogs, elapsedMilliseconds, "OK");
        log.info("[清理] 完成：任务 {} 行、子表 {} 行、旧事件 {} 行、旧审计 {} 行，耗时 {} ms",
                taskRows, childRows, orphanEvents, auditLogs, elapsedMilliseconds);
        return result;
    }

    /** 统计各表当前行数（教学观察用）。 */
    public java.util.Map<String, Integer> tableStats() {
        return java.util.Map.of(
                "research_task", cleanupDao.countOf("research_task"),
                "task_event_log", cleanupDao.countOf("task_event_log"),
                "step_execution", cleanupDao.countOf("step_execution"),
                "source_document", cleanupDao.countOf("source_document"),
                "audit_log", cleanupDao.countOf("audit_log"));
    }
}