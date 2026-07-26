package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.StepExecutionEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchReportRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.SourceDocumentRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.shared.enums.ReportStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责根据步骤执行结果和来源资料组装研究报告。
 */
@Service
public class ResearchReportAssemblyService {

    private final ResearchReportRepository researchReportRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ObjectMapper objectMapper;

    public ResearchReportAssemblyService(
            ResearchReportRepository researchReportRepository,
            SourceDocumentRepository sourceDocumentRepository,
            StepExecutionRepository stepExecutionRepository,
            ObjectMapper objectMapper) {
        this.researchReportRepository = researchReportRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据任务执行结果和来源资料生成最终研究报告。
     */
    @Transactional
    public ResearchReportEntity buildReport(UUID taskId) {
        List<SourceDocumentEntity> sources = sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        List<StepExecutionEntity> executions = stepExecutionRepository.findByTaskIdOrderByStartedAtDesc(taskId);

        JsonNode summarizeOutput = findExecutionOutput(executions, StepType.SUMMARIZE.name());
        JsonNode citationOutput = findExecutionOutput(executions, StepType.CITATION_EXTRACT.name());

        String summary = summarizeOutput != null && summarizeOutput.has("summary")
                ? summarizeOutput.get("summary").asText()
                : "当前研究任务已经完成基础资料收集与整理。";

        Map<String, Object> keyFindings = new LinkedHashMap<>();
        keyFindings.put("summaryFindings", summarizeOutput);
        keyFindings.put("citations", citationOutput);
        keyFindings.put("sourceCount", sources.size());

        String recommendation = "建议继续沿用 Spring Boot + SSE + 分步骤可控执行链的方向推进 MVP。";
        String markdown = buildMarkdown(summary, sources, recommendation);

        ResearchReportEntity report = researchReportRepository.findByTaskId(taskId)
                .orElseGet(ResearchReportEntity::new);
        report.setTaskId(taskId);
        report.setVersion(1);
        report.setSummary(summary);
        report.setKeyFindings(objectMapper.valueToTree(keyFindings));
        report.setFinalRecommendation(recommendation);
        report.setReportMarkdown(markdown);
        report.setReportJson(objectMapper.valueToTree(Map.of(
                "summary", summary,
                "recommendation", recommendation,
                "sources", sources.stream().limit(5).map(SourceDocumentEntity::getUrl).toList()
        )));
        report.setStatus(ReportStatus.FINAL);
        report.setGeneratedAt(OffsetDateTime.now());
        return researchReportRepository.save(report);
    }

    /**
     * 从执行记录中找到指定步骤类型对应的输出结果。
     */
    private JsonNode findExecutionOutput(List<StepExecutionEntity> executions, String stepTypeName) {
        return executions.stream()
                .map(StepExecutionEntity::getOutputPayload)
                .filter(payload -> payload != null && payload.has("stepType"))
                .filter(payload -> stepTypeName.equals(payload.get("stepType").asText()))
                .map(payload -> payload.has("result") ? payload.get("result") : payload)
                .findFirst()
                .orElse(null);
    }

    /**
     * 生成报告的 Markdown 视图。
     */
    private String buildMarkdown(String summary, List<SourceDocumentEntity> sources, String recommendation) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 研究报告\n\n");
        builder.append("## 摘要\n").append(summary).append("\n\n");
        builder.append("## 建议\n").append(recommendation).append("\n\n");
        builder.append("## 参考来源\n");
        sources.stream().limit(5).forEach(source ->
                builder.append("- ")
                        .append(source.getTitle())
                        .append(" - ")
                        .append(source.getUrl())
                        .append("\n"));
        return builder.toString();
    }
}
