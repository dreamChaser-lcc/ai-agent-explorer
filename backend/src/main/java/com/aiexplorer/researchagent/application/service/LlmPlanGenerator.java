package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.application.service.PlanDraft.StepDraft;
import com.aiexplorer.researchagent.infrastructure.config.LlmProperties;
import com.aiexplorer.researchagent.infrastructure.llm.LlmCallGuard;
import com.aiexplorer.researchagent.infrastructure.llm.LlmCallResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 计划生成器（场景⑧ 的核心业务接入点）：
 *
 *   拼提示词 → 交给 LlmCallGuard（超时 + 重试）→ 解析 JSON → 失败则降级为内置模板。
 *
 * 为什么降级放在这里而不是 Guard 里：
 *   Guard 只懂"调用治理"（限时/重试/判定），不懂"计划长什么样"；
 *   兜底模板属于业务语义，放在业务层。
 *
 * 降级策略（可用性优先）：
 *   LLM 挂了不能让用户"任务创建不了"——退化成固定 5 步模板，
 *   plannerModel 标记为 template-fallback，事件表记 LLM_DEGRADED，事后可查。
 */
@Service
public class LlmPlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmPlanGenerator.class);

    /** 合法步骤类型白名单（与 StepType 枚举对齐）。 */
    private static final Set<String> ALLOWED_STEP_TYPES =
            Set.of("SEARCH", "FETCH", "SUMMARIZE", "CITATION_EXTRACT", "REPORT");

    /** 降级时使用的模板模型标记。 */
    private static final String FALLBACK_MODEL_NAME = "template-fallback";

    private final LlmCallGuard llmCallGuard;
    private final ObjectMapper objectMapper;
    private final LlmProperties llmProperties;

    public LlmPlanGenerator(
            LlmCallGuard llmCallGuard,
            ObjectMapper objectMapper,
            LlmProperties llmProperties) {
        this.llmCallGuard = llmCallGuard;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
    }

    /**
     * 生成计划草稿：优先 LLM，失败降级模板。
     *
     * @param researchGoal 研究目标
     * @return 计划草稿（degraded=true 表示走了降级）
     */
    public PlanDraft generate(String researchGoal) {
        String promptText = buildPlanningPrompt(researchGoal);
        LlmCallResult callResult = llmCallGuard.call(promptText);

        if (!callResult.degraded()) {
            try {
                PlanDraft llmDraft = parseLlmDraft(callResult.contentText(), promptText);
                if (llmDraft != null) {
                    log.info("LLM 计划生成成功：模型={}，步骤数={}，尝试={} 次，耗时={} ms",
                            llmDraft.plannerModel(), llmDraft.steps().size(),
                            callResult.attemptCount(), callResult.elapsedMilliseconds());
                    return llmDraft;
                }
                log.warn("LLM 返回内容不含有效步骤，降级为模板计划");
            } catch (Exception exception) {
                log.warn("LLM 返回内容解析失败，降级为模板计划：{}", exception.getMessage());
            }
        }
        return buildFallbackDraft(promptText, callResult);
    }

    /**
     * 组装规划提示词。
     *
     * 强约束三点：只输出 JSON（便于程序解析）、步骤类型限定五个值（便于映射枚举）、
     * 步骤数 3-5（与执行链路匹配）。
     */
    private String buildPlanningPrompt(String researchGoal) {
        return """
                你是 Research Agent 的研究计划规划器。请根据给定的研究目标，输出一份可执行的研究计划。

                输出要求（严格遵守）：
                1. 只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块；
                2. JSON 结构：
                   {"summary":"一句话计划摘要","steps":[{"stepNo":1,"type":"SEARCH","title":"步骤标题","description":"步骤说明"}]}
                3. steps 输出 3-5 个步骤，stepNo 从 1 连续递增；
                4. type 只能取以下五个值之一：SEARCH、FETCH、SUMMARIZE、CITATION_EXTRACT、REPORT；
                5. 计划中【必须包含 CITATION_EXTRACT 步骤】（用于从资料正文提取引用），除非研究目标完全不涉及资料引用；
                6. 所有文本使用中文。

                研究目标：%s
                """.formatted(researchGoal == null ? "" : researchGoal);
    }

    /** 解析 LLM 返回的 JSON；结构不合法返回 null（交给降级）。 */
    private PlanDraft parseLlmDraft(String contentText, String promptText) throws Exception {
        String cleanedJson = stripCodeFence(contentText);
        JsonNode rootNode = objectMapper.readTree(cleanedJson);

        String summaryText = rootNode.path("summary").asText("");
        JsonNode stepsNode = rootNode.path("steps");
        if (!stepsNode.isArray() || stepsNode.isEmpty()) {
            return null;
        }

        List<StepDraft> stepDrafts = new ArrayList<>();
        int fallbackStepNo = 1;
        for (JsonNode stepNode : stepsNode) {
            String stepType = stepNode.path("type").asText("").toUpperCase();
            if (!ALLOWED_STEP_TYPES.contains(stepType)) {
                // 类型不认识就退到 REPORT（宁可步骤类型粗一点，也不要整份计划作废）
                stepType = "REPORT";
            }
            int stepNo = stepNode.path("stepNo").asInt(fallbackStepNo);
            String title = stepNode.path("title").asText("步骤 " + stepNo);
            String description = stepNode.path("description").asText("");
            stepDrafts.add(new StepDraft(stepNo, stepType, title, description));
            fallbackStepNo++;
        }
        // 步骤序号强制连续（LLM 偶尔会跳号）
        List<StepDraft> normalizedSteps = new ArrayList<>();
        for (int index = 0; index < stepDrafts.size(); index++) {
            StepDraft original = stepDrafts.get(index);
            normalizedSteps.add(new StepDraft(index + 1, original.stepType(),
                    original.title(), original.description()));
        }
        return new PlanDraft(
                summaryText.isBlank() ? "已根据研究目标生成研究计划。" : summaryText,
                normalizedSteps,
                "llm:" + llmProperties.chatModel(),
                promptText,
                false,
                null);
    }

    /** 去掉模型有时会附带的 ```json 代码块围栏。 */
    private String stripCodeFence(String contentText) {
        if (contentText == null) {
            return "";
        }
        String trimmed = contentText.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd >= 0) {
                trimmed = trimmed.substring(0, fenceEnd);
            }
        }
        return trimmed.trim();
    }

    /**
     * 降级模板：与研究计划服务原有的固定 5 步保持一致。
     *
     * 这份模板不是"临时凑数"——它是系统在没有 LLM 时的确定性基线：
     * LLM 恢复后自动回到真实计划，前后对比还能看出质量差异。
     */
    private PlanDraft buildFallbackDraft(String promptText, LlmCallResult callResult) {
        List<StepDraft> templateSteps = List.of(
                new StepDraft(1, "SEARCH", "搜索候选资料", "围绕研究目标检索候选资料来源"),
                new StepDraft(2, "FETCH", "抓取资料正文", "抓取候选来源的正文内容"),
                new StepDraft(3, "SUMMARIZE", "总结研究发现", "对抓取内容进行总结归纳"),
                new StepDraft(4, "CITATION_EXTRACT", "提取引用信息", "整理用于报告展示的引用来源"),
                new StepDraft(5, "REPORT", "生成研究报告", "汇总研究结果并生成最终报告"));
        String reason = callResult.degradedReason() == null
                ? "LLM 响应不可用" : callResult.degradedReason();
        log.warn("计划生成降级为模板：{}（尝试 {} 次，耗时 {} ms）",
                reason, callResult.attemptCount(), callResult.elapsedMilliseconds());
        return new PlanDraft(
                "系统已根据研究目标生成初版研究计划（LLM 不可用，已降级为模板）。",
                templateSteps,
                FALLBACK_MODEL_NAME,
                promptText,
                true,
                reason);
    }
}
