package com.aiexplorer.researchagent.application.service;

import java.util.List;

/**
 * 计划草稿：LLM 生成结果 或 模板降级结果 的统一载体。
 *
 * degraded = true 表示本次没有拿到可用的 LLM 计划，steps 来自内置模板——
 * 业务上仍然可以继续（任务照常进入"等待确认"），只是质量退化为固定模板。
 */
public record PlanDraft(
        String summary,
        List<StepDraft> steps,
        String plannerModel,
        String promptSnapshot,
        boolean degraded,
        String degradedReason) {

    /**
     * 单个步骤草稿。
     *
     * @param stepNo      步骤序号（1 开始）
     * @param stepType    步骤类型字符串（SEARCH / FETCH / SUMMARIZE / CITATION_EXTRACT / REPORT）
     * @param title       步骤标题
     * @param description 步骤说明
     */
    public record StepDraft(
            int stepNo,
            String stepType,
            String title,
            String description) {
    }
}
