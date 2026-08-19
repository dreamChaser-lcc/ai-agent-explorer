package com.aiexplorer.researchagent.infrastructure.persistence.entity; // 基础设施层 - 持久化实体

import com.aiexplorer.researchagent.shared.enums.ExecutionMode; // 执行模式（SYNC / ASYNC）
import com.aiexplorer.researchagent.shared.enums.TaskStage;     // 任务阶段（PLANNING / EXECUTING / REPORTING）
import com.aiexplorer.researchagent.shared.enums.TaskStatus;    // 任务状态（QUEUED / RUNNING / COMPLETED ...）
import jakarta.persistence.Column;             // 数据库列映射
import jakarta.persistence.Entity;             // 标记这是 JPA 实体
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;          // 枚举字段映射：数据库存字符串而非数字
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;                  // 主键
import jakarta.persistence.Table;               // 表名映射
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;      // Lombok：自动生成 getter 方法
import lombok.NoArgsConstructor; // Lombok：自动生成无参构造器
import lombok.Setter;      // Lombok：自动生成 setter 方法

/**
 * 研究任务实体（JPA Entity），映射到 research_task 表。
 *
 * 职责：作为数据库和 Java 对象之间的桥梁。
 * JPA 的作用：
 *   - @Entity → 告诉 JPA 这是一个数据库表的映射类
 *   - @Table → 指定对应的表名
 *   - @Column → 指定列名和约束
 *   - JpaRepository 的 save() / findById() 等操作的都是这个实体类
 *
 * 类比前端：
 *   Prisma schema → model ResearchTask { ... }
 *   TypeScript interface → 这就是数据库行的 Java 版本
 *
 * @Getter / @Setter 是 Lombok 注解，编译时自动生成所有字段的 getter/setter，
 * 避免手写大量样板代码。
 */
@Getter               // Lombok 自动为所有字段生成 getXxx() 方法
@Setter               // Lombok 自动为所有字段生成 setXxx() 方法
@NoArgsConstructor    // Lombok 自动生成无参构造器（JPA 要求）
@Entity               // 声明这是一个 JPA 实体，Spring 会自动扫描
@Table(name = "research_task") // 映射到数据库的 research_task 表
public class ResearchTaskEntity extends BaseTimeEntity { // 继承 BaseTimeEntity，自动拥有 createdAt/updatedAt

    /**
     * 主键：UUID 类型。
     * GenerationType.UUID → 由 JPA 自动生成 UUID（H2/PostgreSQL 均支持）。
     */
    @Id // 标记主键
    @GeneratedValue(strategy = GenerationType.UUID) // 自动生成 UUID
    private UUID id;

    /**
     * 显示编号 TASK-xxxxxxxx，便于人工识别。
     * unique = true → 数据库唯一约束，不能重复。
     */
    @Column(name = "task_no", nullable = false, unique = true, length = 64)
    private String taskNo;

    /**
     * 任务标题：用户输入的研究主题名称。
     */
    @Column(nullable = false)
    private String title;

    /**
     * 研究目标：用户输入的具体研究需求描述。
     * columnDefinition = "TEXT" → 使用数据库的 TEXT 类型（长文本）。
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    /**
     * 执行模式：SYNC 同步 / ASYNC 异步。
     * EnumType.STRING → 数据库存 "SYNC"/"ASYNC"，而不是 0/1。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 32)
    private ExecutionMode executionMode;

    /**
     * 任务状态：QUEUED → WAITING_FOR_CONFIRMATION → RUNNING → COMPLETED / FAILED / PAUSED / CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaskStatus status;

    /**
     * 当前阶段：PLANNING / EXECUTING / REPORTING
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 64)
    private TaskStage currentStage;

    /**
     * 当前正在执行的步骤 ID（null 表示没有正在执行的步骤）。
     */
    @Column(name = "current_step_id")
    private UUID currentStepId;

    /**
     * 是否需要人工确认：计划生成后为 true，用户确认后为 false。
     */
    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    /**
     * 优先级：数字越大优先级越高，默认 0。
     */
    @Column(nullable = false)
    private int priority;

    /**
     * 错误消息：仅当任务失败时非空。
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建者标识，MVP 阶段固定为 "demo-user"。
     */
    @Column(name = "created_by", length = 128)
    private String createdBy;

    /**
     * 任务开始执行的时间。
     */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /**
     * 任务完成的时间。
     */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
