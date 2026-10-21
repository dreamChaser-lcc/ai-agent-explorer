package com.aiexplorer.researchagent.infrastructure.llm;

/**
 * LLM 调用结果（含治理过程信息）。
 *
 * 与"直接返回字符串"相比，这里额外带回：
 *   - degraded：是否已经放弃调用、需要走降级
 *   - degradedReason：最后一次失败原因（超时 / 500 / 解析异常……）
 *   - attemptCount：实际尝试了几次（重试了几轮）
 *   - elapsedMilliseconds：总耗时（含退避等待）
 * 这三项是排障与监控的依据——"失败"本身不可怕，不知道为什么失败才可怕。
 */
public record LlmCallResult(
        String contentText,
        boolean degraded,
        String degradedReason,
        int attemptCount,
        long elapsedMilliseconds) {

    /** 调用成功（未降级）的快捷构造。 */
    public static LlmCallResult success(String contentText, int attemptCount, long elapsedMilliseconds) {
        return new LlmCallResult(contentText, false, null, attemptCount, elapsedMilliseconds);
    }

    /** 降级（所有尝试均失败）的快捷构造。 */
    public static LlmCallResult degraded(String reason, int attemptCount, long elapsedMilliseconds) {
        return new LlmCallResult(null, true, reason, attemptCount, elapsedMilliseconds);
    }
}
