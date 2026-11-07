package com.aiexplorer.researchagent.infrastructure.persistence.entity.requestPipline;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 请求管线练习 · 持久化对象（PO）——对应 request_pipline 表。
 *
 * <p><b>为什么没有 JPA 注解</b>：本练习的 DAO 是 MyBatis（XML 显式写 SQL），
 * MyBatis 只依赖 getter/setter，不需要 @Entity/@Column：
 * <ul>
 *   <li>字段 ↔ 列名 的映射写在 XML 里（{@code #{po.requestId} → request_id}），一眼可见；</li>
 *   <li>不加 @Entity → 不会进入 Hibernate 的 {@code ddl-auto: validate} 校验
 *       （表结构以 Flyway 为准，练习起步阶段少一处启动失败点）。</li>
 * </ul>
 * 若将来要改走 JPA Repository，再加 @Entity/@Table/@Id/@Column 即可。</p>
 *
 * <p><b>它在链路中的位置</b>（本练习要找的那一环）：</p>
 * <pre>
 *   Controller（收 DTO）
 *     → Service：new RequestPiplineEntity()   ← ★ PO 出生
 *     → Mapper.insert(PO) → DB
 *     ← DB 行 → PO
 *     → 手工转换：PO → VO                     ← PO 死亡（不出 Service）
 *   → Controller 返回 VO
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestPiplineEntity {

    /** 主键（Service 里手动 UUID 生成——MyBatis 不参与主键分配）。 */
    private UUID id;

    /** 业务请求 id（唯一索引；对应列 request_id）。 */
    private String requestId;

    /** 请求类型：CREATE / UPDATE（存枚举名；对应列 request_type）。 */
    private String requestType;

    /** 请求描述（对应列 description）。 */
    private String description;

    /** 创建时间（对应列 create_time）。 */
    private OffsetDateTime createTime;

    /** 更新时间（对应列 update_time，更新时刷新）。 */
    private OffsetDateTime updateTime;
}