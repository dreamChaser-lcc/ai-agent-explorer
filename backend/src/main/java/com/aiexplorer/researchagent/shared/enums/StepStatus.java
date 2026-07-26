package com.aiexplorer.researchagent.shared.enums;

/**
 * 表示计划步骤的当前状态。
 */
public enum StepStatus {
    PENDING,
    READY,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    SKIPPED
}
