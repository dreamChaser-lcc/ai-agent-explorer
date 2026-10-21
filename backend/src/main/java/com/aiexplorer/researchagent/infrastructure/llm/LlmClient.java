package com.aiexplorer.researchagent.infrastructure.llm;

/**
 * LLM 下游调用的最小抽象：输入一段提示词，输出一段文本。
 *
 * 为什么要抽象：
 *   1. 真实实现（OpenAiCompatibleLlmClient）依赖网络与第三方额度，本地无法稳定验证异常路径；
 *   2. 模拟实现（SimulatedLlmClient）可精确制造"慢"与"失败"，用来验证超时 / 重试 / 降级；
 *   3. 上层（LlmCallGuard / LlmPlanGenerator）只依赖接口，换供应商不改业务代码。
 */
public interface LlmClient {

    /**
     * 发起一次 LLM 补全调用（不包含重试与超时治理——那些由 LlmCallGuard 负责）。
     *
     * @param promptText 提示词
     * @return 模型返回的文本内容
     */
    String complete(String promptText);

    /**
     * 描述当前实现（写入日志与计划记录，便于回溯"这份计划是谁生成的"）。
     */
    String describeImplementation();
}
