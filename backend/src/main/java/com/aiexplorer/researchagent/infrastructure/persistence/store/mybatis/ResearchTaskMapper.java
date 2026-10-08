package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis 版研究任务数据访问接口（列表分页/筛选专用，场景③）。
 *
 * <p>接口只声明方法签名，SQL 全部维护在同名 XML 中
 * （resources/mapper/ResearchTaskMapper.xml）——"接口与 SQL 分离"。</p>
 *
 * <p><b>混合持久化的位置声明</b>：任务实体的【写入】仍走 JPA
 * （沿用 @Version 乐观锁等既有机制）；【列表查询】这条读路径改走 MyBatis 手写 SQL。
 * "简单写走 ORM、复杂读走手写 SQL"是企业里常见的混合形态——
 * 本项目更进一步：连简单读也手写（学习目的：把 SQL 语法练出来）。</p>
 */
@Mapper
public interface ResearchTaskMapper {

    /**
     * 分页查询任务列表（可按状态筛选，按创建时间倒序）。
     *
     * @param status 状态筛选（null = 不筛选，查全部）
     * @param size   每页条数
     * @param offset 偏移量（跳过前多少条——公式：offset = page × size）
     */
    List<ResearchTaskEntity> findPage(@Param("status") TaskStatus status,
                                      @Param("size") int size,
                                      @Param("offset") long offset);

    /**
     * 统计满足筛选条件的总条数（用于计算总页数）。
     */
    long countPage(@Param("status") TaskStatus status);
}
