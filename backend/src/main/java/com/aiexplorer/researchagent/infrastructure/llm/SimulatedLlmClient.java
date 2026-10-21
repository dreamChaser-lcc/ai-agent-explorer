package com.aiexplorer.researchagent.infrastructure.llm;

import com.aiexplorer.researchagent.infrastructure.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可控模拟客户端：用于稳定复现"下游慢"与"下游失败"，验证超时 / 重试 / 降级链路。
 *
 * 模式（app.llm.simulate-mode）：
 *   NORMAL → 200ms 后返回一份"看起来像 LLM 生成"的计划 JSON（正常路径）
 *   SLOW   → 睡足 超时秒数 + 2 秒（触发 Guard 的超时 → 重试 → 降级）
 *   ERROR  → 直接抛异常（模拟 500，触发重试 → 降级）
 *
 * 为什么必须要有它：真实中转站不会配合你慢 5 秒或返回 500，
 * 没有可控下游就无法验证治理逻辑是否真的生效（场景⑦"直改库"实验的同款思路）。
 */
public class SimulatedLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedLlmClient.class);

    /** NORMAL 模式下的"思考耗时"，模拟真实网络的往返延迟。 */
    private static final long NORMAL_DELAY_MILLISECONDS = 200L;

    private final LlmProperties llmProperties;

    public SimulatedLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public String complete(String promptText) {
        String mode = llmProperties.simulateMode() == null
                ? "NORMAL" : llmProperties.simulateMode().trim().toUpperCase();

        switch (mode) {
            case "SLOW" -> {
                long slowDelay = (Math.max(1, llmProperties.timeoutSeconds()) + 2L) * 1000L;
                log.info("模拟下游【慢】：睡眠 {} ms（超时阈值为 {} 秒）", slowDelay, llmProperties.timeoutSeconds());
                sleepQuietly(slowDelay);
                return buildSimulatedPlanJson();
            }
            case "ERROR" -> {
                log.info("模拟下游【失败】：抛出 HTTP 500 等价异常");
                throw new IllegalStateException("模拟下游失败：HTTP 500 Internal Server Error");
            }
            default -> {
                log.info("模拟下游【正常】：{} ms 后返回计划", NORMAL_DELAY_MILLISECONDS);
                sleepQuietly(NORMAL_DELAY_MILLISECONDS);
                return buildSimulatedPlanJson();
            }
        }
    }

    @Override
    public String describeImplementation() {
        String mode = llmProperties.simulateMode() == null ? "NORMAL" : llmProperties.simulateMode();
        return "simulated[" + mode.toUpperCase() + "]";
    }

    /**
     * 模拟 LLM 输出的计划 JSON —— 格式与真实提示词要求的返回格式完全一致，
     * 这样上下游解析逻辑在两种实现下都能验证。
     */
    private String buildSimulatedPlanJson() {
        return """
                {
                  "summary": "模拟计划：先检索权威资料，再交叉验证关键结论，最后输出带引用的研究报告。",
                  "steps": [
                    {"stepNo": 1, "type": "SEARCH", "title": "检索权威资料", "description": "围绕研究目标检索 3-5 个权威来源"},
                    {"stepNo": 2, "type": "FETCH", "title": "抓取并清洗正文", "description": "抓取正文并去除导航与广告噪声"},
                    {"stepNo": 3, "type": "SUMMARIZE", "title": "交叉验证并总结", "description": "对多来源结论做交叉验证后归纳"},
                    {"stepNo": 4, "type": "CITATION_EXTRACT", "title": "抽取引用", "description": "记录来源 URL 与关键引文"},
                    {"stepNo": 5, "type": "REPORT", "title": "生成研究报告", "description": "按摘要-发现-结论结构输出"}
                  ]
                }
                """;
    }

    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
