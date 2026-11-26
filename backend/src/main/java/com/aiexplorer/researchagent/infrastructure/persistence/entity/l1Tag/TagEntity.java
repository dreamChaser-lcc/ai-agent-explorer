package com.aiexplorer.researchagent.infrastructure.persistence.entity.l1Tag;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 标签实体（PO，对应表 {@code l1_tag}）。
 *
 * <p><b>说明</b>：</p>
 * <ul>
 *   <li>纯 MyBatis POJO——不加 JPA 的 {@code @Entity/@Table}（本项目持久化层用 MyBatis，
 *       加 JPA 注解会误导读者，也可能被 JPA 自动配置扫描）；</li>
 *   <li>字段名与表列名用"下划线 ↔ 驼峰"映射（依赖 MyBatis 的
 *       {@code map-underscore-to-camel-case=true}，全局配置已开启）；</li>
 *   <li>时间类型用 {@link OffsetDateTime}（与项目其它实体保持一致）。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "标签实体")
public class TagEntity {

    /** 主键（UUID，由 Service 层生成后传入，不用数据库自增）。 */
    private UUID id;

    /** 标签名（业务唯一，2~32 字符）。 */
    private String name;

    /** 描述（可为空，最长 200 字符）。 */
    private String description;

    /** 状态：ACTIVE / DISABLED（用 {@code TagStatus} 枚举名存储）。 */
    private String status;

    /** 创建时间（插入时由 Service 赋值）。 */
    private OffsetDateTime createTime;

    /** 更新时间（插入/更新时由 Service 赋值）。 */
    private OffsetDateTime updateTime;
}
