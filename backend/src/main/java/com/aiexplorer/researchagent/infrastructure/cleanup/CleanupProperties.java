package com.aiexplorer.researchagent.infrastructure.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 场景⑩：数据保留策略配置（app.cleanup.*）。
 *
 * 所有阈值都可通过环境变量覆盖：
 *   APP_CLEANUP_TASK_RETENTION_DAYS=30  等
 */
@ConfigurationProperties(prefix = "app.cleanup")
public class CleanupProperties {

    /** 总开关（生产可关闭，改用外部调度平台）。 */
    private boolean enabled = true;

    /** 已完成/已取消/失败的任务保留天数，超期删除（连同子表）。 */
    private int taskRetentionDays = 30;

    /** 事件日志保留天数（无主事件按此清理）。 */
    private int eventRetentionDays = 60;

    /** 审计日志保留天数（合规要求通常更长）。 */
    private int auditRetentionDays = 90;

    /** 单批删除条数（避免一次删太多造成长事务/锁表）。 */
    private int batchSize = 200;

    /** 调度间隔（毫秒），默认 1 小时。 */
    private long intervalMs = 3_600_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTaskRetentionDays() {
        return taskRetentionDays;
    }

    public void setTaskRetentionDays(int taskRetentionDays) {
        this.taskRetentionDays = taskRetentionDays;
    }

    public int getEventRetentionDays() {
        return eventRetentionDays;
    }

    public void setEventRetentionDays(int eventRetentionDays) {
        this.eventRetentionDays = eventRetentionDays;
    }

    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public void setAuditRetentionDays(int auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }
}