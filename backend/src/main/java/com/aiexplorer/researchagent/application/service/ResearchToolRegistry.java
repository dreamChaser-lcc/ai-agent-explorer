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
 * 研究工具注册表（策略模式）。
 *
 * 职责：
 *   1. 自动收集 Spring 容器中所有实现了 ResearchTool 接口的 Bean
 *   2. 根据 stepType（SEARCH / FETCH / SUMMARIZE / CITATION_EXTRACT）
 *      找到对应的工具并执行
 *
 * 为什么需要这个？
 *   编码层（ResearchTaskOrchestrator）不需要知道具体有哪些工具，
 *    只需要说"我要执行 SEARCH 类型的步骤"，
 *   注册表负责找到 WebSearchResearchTool 并调用它。
 *
 *   这样新增工具（比如加一个学术论文搜索工具）时，
 *   只需写一个新类实现 ResearchTool，注册表自动纳入，
 *   编排层一行代码都不用改——符合"开闭原则"。
 */
@Service // 注册为 Spring Bean
public class ResearchToolRegistry {

    // EnumMap：以 StepType 枚举为 key，ResearchTool 实例为 value 的映射表
    // StepType 是有限的 5 个枚举值，EnumMap 比 HashMap 性能更好
    private final Map<StepType, ResearchTool> toolByStepType;

    /**
     * 构造器：Spring 自动注入所有实现了 ResearchTool 接口的 Bean。
     *
     * List<ResearchTool> tools 参数——Spring 会找出所有 @Component 的实现类，
     * 打包成一个 List 注入进来。
     *
     * 示例：容器里有 WebSearchResearchTool、WebFetchResearchTool 等 4 个类，
     *       这列表就包含 4 个元素。
     */
    public ResearchToolRegistry(List<ResearchTool> tools) {
        this.toolByStepType = new EnumMap<>(StepType.class);
        // 遍历所有工具，以 stepType 为 key 存入映射
        // 每个工具的 getSupportedStepType() 返回它负责处理的步骤类型
        for (ResearchTool tool : tools) {
            this.toolByStepType.put(tool.getSupportedStepType(), tool);
        }
    }

    /**
     * 根据步骤类型找到对应工具并执行。
     *
     * @param stepType 步骤类型（SEARCH / FETCH / SUMMARIZE / CITATION_EXTRACT）
     * @param context  工具执行上下文（含任务信息 + 已有资料来源）
     * @return 工具执行结果（outputPayload + sourceDocuments）
     * @throws IllegalStateException 如果找不到对应工具（理论上不会发生）
     */
    public ResearchToolResult execute(StepType stepType, ResearchToolContext context) {
        ResearchTool tool = toolByStepType.get(stepType);
        if (tool == null) {
            throw new IllegalStateException("未找到步骤类型对应的研究工具: " + stepType);
        }
        return tool.execute(context);
    }
}
