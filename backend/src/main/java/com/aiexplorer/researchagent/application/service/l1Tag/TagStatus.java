package com.aiexplorer.researchagent.application.service.l1Tag;

/**
 * 标签状态（持久化到 l1_tag.status 列）。
 *
 * <p>注意：状态描述"标签当前处于什么状态"，不是"发生了什么动作"——
 * 所以是 ACTIVE/DISABLED，而不是 CREATE/UPDATE/DELETE（那是事件类型，属于 L6 的 MQ 事件）。</p>
 */
public enum TagStatus {
    /** 启用（可被任务引用） */
    ACTIVE,
    /** 停用（保留但不再被引用） */
    DISABLED
}
