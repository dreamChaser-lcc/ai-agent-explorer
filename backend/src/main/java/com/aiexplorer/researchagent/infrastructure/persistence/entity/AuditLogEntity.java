package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.AuditResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 操作审计实体（场景⑤），映射到 audit_log 表（本项目第 9 张表）。
 *
 * <p>与 {@link TaskEventLogEntity} 的分工（别混）：
 * <ul>
 *   <li>task_event_log = "任务发生了什么"（业务事件流——给前端时间线看）；</li>
 *   <li>audit_log = "谁按的按钮"（责任审计流——给排查 / 合规用）。</li>
 * </ul></p>
 *
 * <p>★ 审计记录【只插入、不更新】（append-only）——所以不继承 BaseTimeEntity
 * （那个是"可变记录"基类、带 updated_at），只留 createdAt 一列——与 task_event_log 同风格。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 谁——操作者（MVP 阶段固定 "demo-user"；正式方案 = 从用户/安全上下文取）。 */
    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    /** 干了什么——操作名（来自 @AuditLog 注解的 value）。 */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** 操作对象类型（MVP 固定 "RESEARCH_TASK"）。 */
    @Column(name = "target_type", length = 64)
    private String targetType;

    /** 操作对象 id（从方法参数里抓的第一个 UUID——如 taskId；创建类操作可能为 null）。 */
    @Column(name = "target_id", length = 64)
    private String targetId;

    /** 结局：SUCCESS / FAILED（★ 失败也要记）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private AuditResult result;

    /** 失败原因（FAILED 时才有；超长会被截断——列宽 512）。 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 补充细节（预留给未来扩展——如"确认还是拒绝"、变更前后值）。 */
    @Column(name = "detail", length = 512)
    private String detail;

    /** 何时发生。 */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
