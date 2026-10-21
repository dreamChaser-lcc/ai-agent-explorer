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
    REPORT_GENERATED,
    /** 场景⑧：LLM 下游不可用，计划生成已降级为内置模板（事后可查降级原因）。 */
    LLM_DEGRADED
}
