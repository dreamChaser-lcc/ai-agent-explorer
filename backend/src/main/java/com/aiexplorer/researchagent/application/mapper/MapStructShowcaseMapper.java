package com.aiexplorer.researchagent.application.mapper;

import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * T2 演示集合：MapStruct 的典型用法（对照 docs/技术栈补齐实战/T2·MapStruct映射.md）。
 *
 * <p>先回答一个常见疑问："是不是只有全参数匹配？"——不是。
 * {@code toSummary(ResearchTaskEntity entity)} 只是【一个入参 + 一个返回值】的普通方法签名，
 * MapStruct 的映射粒度是【字段级】：入参对象的 getter 字段 ↔ 目标对象的构造参数/属性。</p>
 *
 * <p>本类覆盖：① 单源自动映射 ② 多源合并（default 组装，IDE 友好） ③ 集合映射
 * ④ {@code @MappingTarget} 更新已有对象。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MapStructShowcaseMapper {

    /**
     * 例 1：单源 · 同名字段全自动（基础态）。
     *
     * <p>实体 getter 与 record 组件同名同类型 → 零配置；boolean isXxx() 自动识别为 xxx。</p>
     */
    TaskSummaryResponse toSummary(ResearchTaskEntity entity);

    /**
     * 例 2：多源合并 —— 用 {@code default} 方法组装（IDE 友好的写法）。
     *
     * <p>为什么不用 MapStruct 的"多源参数"直接映射？实测踩坑记录：</p>
     * <ul>
     *   <li>多源参数依赖编译期保留参数名（{@code -parameters}）。Maven 侧已配置
     *       {@code <parameters>true</parameters>}，命令行构建正常；</li>
     *   <li>但 IDE（Eclipse JDT 系，含 VSCode Java 扩展）不传 {@code -parameters} 时，
     *       MapStruct 解析不到 "task"/"plan" → 报
     *       {@code No implementation was created ... erroneous element null} 且 build 仍能过；</li>
     *   <li>且多源同名字段（id/status/createdAt/updatedAt 两边都有）还会报
     *       {@code Several possible source properties}，必须逐字段消歧义。</li>
     * </ul>
     *
     * <p>本方法改用"单源生成 + 手工补字段"：MapStruct 只生成单源映射
     * （{@link #toDetailFromTask}），跨源合并的 3 行逻辑写在 default 方法里——
     * 编译期零参数名依赖，IDE 与命令行行为一致。</p>
     */
    default TaskDetailResponse toDetail(ResearchTaskEntity task, ResearchPlanEntity plan) {
        TaskDetailResponse base = toDetailFromTask(task);
        if (base == null) {
            return null;
        }
        return new TaskDetailResponse(
                base.id(),
                base.taskNo(),
                base.title(),
                base.goal(),
                base.executionMode(),
                base.status(),
                base.currentStage(),
                base.requiresConfirmation(),
                plan != null ? plan.getPlanSummary() : null,   // ← 名字不同：手工桥接
                null,                                          // plannedSteps：无来源
                base.createdAt(),
                base.updatedAt());
    }

    /**
     * 例 2 的内部实现：单源映射（MapStruct 编译期生成实现，字段全自动）。
     *
     * <p>latestPlanSummary / plannedSteps 在单个 Entity 上无来源，显式 ignore
     * 以避免 unmapped 警告（由 toDetail 的 default 方法手工填充）。</p>
     */
    @Mapping(target = "latestPlanSummary", ignore = true)
    @Mapping(target = "plannedSteps", ignore = true)
    TaskDetailResponse toDetailFromTask(ResearchTaskEntity task);

    /**
     * 例 3：集合映射 —— List 自动逐元素转换。
     *
     * <p>MapStruct 生成 for 循环并复用 toSummary；无需手写 stream。</p>
     */
    List<TaskSummaryResponse> toSummaries(List<ResearchTaskEntity> entities);

    /**
     * 例 4：{@code @MappingTarget} —— 更新【已有对象】，不创建新实例。
     *
     * <p>对比：普通映射返回新对象；@MappingTarget 把源字段逐个 set 进目标
     * （要求目标有 setter —— 所以 record 不能当 target，Entity 可以）。</p>
     */
    void copyInto(ResearchTaskEntity source, @MappingTarget ResearchTaskEntity target);
}
