package com.aiexplorer.researchagent.shared.enums;

/**
 * 表示研究任务的顶层生命周期状态。
 */
public enum TaskStatus {
    DRAFT,
    QUEUED,
    PLANNING,
    WAITING_FOR_CONFIRMATION,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
