package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;

/**
 * "创建任务"用例的内部结果（场景④）。
 *
 * <p>不仅给任务摘要，还告诉调用方【本次是否真的新建了】——
 * Controller 用它决定 HTTP 状态码：新建 → 201 Created；重放（幂等命中）→ 200 OK。
 * （record 的又一用途：一个方法"返回多个值"的轻量载体。）</p>
 *
 * @param task         任务摘要（新建的，或幂等重放的首次结果）
 * @param newlyCreated true = 本次真创建；false = 幂等重放（同键的重复请求）
 */
public record CreateTaskOutcome(TaskSummaryResponse task, boolean newlyCreated) {
}
