package com.aiexplorer.researchagent.shared.enums;

/**
 * 表示任务生命周期内会记录的事件类型。
 */
public enum EventType {
    TASK_CREATED,
    PLAN_GENERATED,
    PLAN_CONFIRMED,
    PLAN_REJECTED,
    TASK_PAUSED,
    TASK_RESUMED,
    TASK_CANCELLED,
    STEP_STARTED,
    STEP_FINISHED,
    STEP_FAILED,
    REPORT_GENERATED
}
