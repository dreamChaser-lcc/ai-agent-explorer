package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.tools.ResearchTool;
import com.aiexplorer.researchagent.infrastructure.tools.ResearchToolContext;
import com.aiexplorer.researchagent.infrastructure.tools.ResearchToolResult;
import com.aiexplorer.researchagent.shared.enums.StepType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 负责根据步骤类型分发研究工具。
 */
@Service
public class ResearchToolRegistry {

    private final Map<StepType, ResearchTool> toolByStepType;

    /**
     * 根据所有工具实现构建步骤类型到工具实例的映射。
     */
    public ResearchToolRegistry(List<ResearchTool> tools) {
        this.toolByStepType = new EnumMap<>(StepType.class);
        for (ResearchTool tool : tools) {
            this.toolByStepType.put(tool.getSupportedStepType(), tool);
        }
    }

    /**
     * 根据步骤类型找到对应工具并执行。
     */
    public ResearchToolResult execute(StepType stepType, ResearchToolContext context) {
        ResearchTool tool = toolByStepType.get(stepType);
        if (tool == null) {
            throw new IllegalStateException("未找到步骤类型对应的研究工具: " + stepType);
        }
        return tool.execute(context);
    }
}
