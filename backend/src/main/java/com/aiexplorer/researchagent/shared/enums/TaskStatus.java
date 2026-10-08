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
    CANCELLED;

    /**
     * 是否为终态：任务已经结束，不应再发生任何状态迁移。
     * （J6 学的"枚举带方法"实战——把"哪些算结束"这个规则收在枚举自己身上。）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
