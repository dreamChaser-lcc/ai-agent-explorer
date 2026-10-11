package com.aiexplorer.researchagent.shared.enums;

/**
 * 审计结果（场景⑤）：一次操作的最终结局。
 *
 * <p>★ "失败也要记"是审计的硬要求——失败的尝试往往比成功的更值得追溯
 * （谁试图干了什么、但没干成、为什么没干成）。</p>
 */
public enum AuditResult {

    /** 操作成功完成 */
    SUCCESS,

    /** 操作失败（业务异常 / 状态机拒绝等——原因见 error_message） */
    FAILED
}
