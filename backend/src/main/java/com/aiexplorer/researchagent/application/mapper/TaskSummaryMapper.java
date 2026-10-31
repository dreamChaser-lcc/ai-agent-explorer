package com.aiexplorer.researchagent.application.mapper;

import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * T2 演示：MapStruct 版映射（编译期生成实现类）。
 *
 * <p>对照手写版 {@code TaskResponseMapper.toTaskSummary}（9 个字段手工 new）：</p>
 * <pre>
 * 手写：9 行参数逐个摆——漏一个字段编译照样过（运行时得到 null）
 * MapStruct：接口 1 个方法声明——编译期比对两边字段，对不齐会警告/报错，
 *            并在 target/generated-sources/annotations 生成 TaskSummaryMapperImpl
 * </pre>
 *
 * <p>字段映射规则（本接口零配置即可工作）：</p>
 * <ul>
 *   <li>同名同类型 → 自动映射（taskNo→taskNo、title→title ...）</li>
 *   <li>boolean getter 的 {@code isXxx()} → 目标组件 {@code xxx}（requiresConfirmation）</li>
 *   <li>目标 {@code TaskSummaryResponse} 是 record（J1）→ MapStruct 走全参构造器</li>
 * </ul>
 *
 * <p>componentModel = SPRING：生成的 Impl 带 @Component，可直接注入使用。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TaskSummaryMapper {

    TaskSummaryResponse toSummary(ResearchTaskEntity entity);
}
