package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.shared.enums.StepType;

/**
 * 定义研究工具的统一执行接口。
 */
public interface ResearchTool {

    StepType getSupportedStepType();

    ResearchToolResult execute(ResearchToolContext context);
}
