package com.aiexplorer.researchagent.infrastructure.cleanup;

/**
 * 场景⑩：一次清理的结果统计（用于日志与接口返回）。
 */
public record CleanupResult(
        int tasks,
        int childRows,
        int orphanEvents,
        int auditLogs,
        long elapsedMs,
        String status) {

    /** 总开关关闭时的结果。 */
    public static CleanupResult skipped() {
        return new CleanupResult(0, 0, 0, 0, 0, "SKIPPED(disabled)");
    }

    /** 没拿到锁（别的实例正在清理）。 */
    public static CleanupResult locked() {
        return new CleanupResult(0, 0, 0, 0, 0, "SKIPPED(locked by another instance)");
    }
}