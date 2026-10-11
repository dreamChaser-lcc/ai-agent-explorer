CREATE TABLE IF NOT EXISTS research_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    goal TEXT NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    current_stage VARCHAR(64) NOT NULL,
    current_step_id UUID NULL,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_by VARCHAR(128) NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 兼容已有库：老库（表已存在）缺 version 列时补上（IF NOT EXISTS → 本文件可重复执行）
ALTER TABLE research_task ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- 场景④：幂等键列（客户端带来的"请求唯一标记"——重复提交在数据库层被拦下）
ALTER TABLE research_task ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

-- 场景③：列表查询索引（手写 SQL 的搭档——让"排序 + 分页 + 筛选"走索引）
-- idx_research_task_created_at：ORDER BY created_at DESC + LIMIT 直接走索引（免全表扫、免排序）
-- idx_research_task_status    ：WHERE status = ? 筛选走索引
CREATE INDEX IF NOT EXISTS idx_research_task_created_at ON research_task (created_at);
CREATE INDEX IF NOT EXISTS idx_research_task_status ON research_task (status);

-- 场景④：幂等键唯一索引（唯一约束 = 并发仲裁者）
-- ★ 多个 NULL 不冲突：不带幂等键的老请求互不影响；同键并发时数据库作最终仲裁
CREATE UNIQUE INDEX IF NOT EXISTS uq_research_task_idempotency_key ON research_task (idempotency_key);

CREATE TABLE IF NOT EXISTS research_plan (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    version INTEGER NOT NULL,
    plan_summary TEXT NOT NULL,
    plan_objective TEXT NOT NULL,
    status VARCHAR(64) NOT NULL,
    confirmation_status VARCHAR(64) NOT NULL,
    planner_model VARCHAR(128) NULL,
    planner_prompt_snapshot TEXT NULL,
    raw_plan_payload JSON NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS research_step (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    step_no INTEGER NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    input_payload JSON NULL,
    expected_output TEXT NULL,
    status VARCHAR(64) NOT NULL,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    depends_on_step_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS step_execution (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    step_id UUID NOT NULL,
    attempt_no INTEGER NOT NULL,
    executor_type VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128) NULL,
    status VARCHAR(64) NOT NULL,
    input_payload JSON NULL,
    output_payload JSON NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    duration_ms BIGINT NULL
);

CREATE TABLE IF NOT EXISTS source_document (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    step_id UUID NULL,
    source_type VARCHAR(64) NOT NULL,
    url TEXT NULL,
    domain VARCHAR(255) NULL,
    title VARCHAR(512) NULL,
    snippet TEXT NULL,
    raw_content TEXT NULL,
    content_hash VARCHAR(128) NULL,
    language VARCHAR(32) NULL,
    fetch_status VARCHAR(64) NOT NULL,
    relevance_score DOUBLE NULL,
    citation_ready BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSON NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS research_report (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE,
    version INTEGER NOT NULL,
    summary TEXT NOT NULL,
    key_findings JSON NULL,
    final_recommendation TEXT NULL,
    report_markdown TEXT NULL,
    report_json JSON NULL,
    status VARCHAR(64) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS task_event_log (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    step_id UUID NULL,
    event_type VARCHAR(64) NOT NULL,
    event_message TEXT NOT NULL,
    event_payload JSON NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(128) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS human_confirmation (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    step_id UUID NULL,
    confirmation_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    request_message TEXT NOT NULL,
    response_message TEXT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE NULL,
    requested_by VARCHAR(128) NULL,
    responded_by VARCHAR(128) NULL
);

-- 场景⑤：操作审计表（第 9 张表）——"谁、何时、干了什么"
-- 与 task_event_log 的分工：那边 = 业务事件流（任务发生了什么）；这边 = 责任审计流（谁按的按钮）
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    result VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    detail VARCHAR(512) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 审计查询主路径：按时间倒序翻最新操作（排查的第一动作）
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
-- 按操作名筛选（如"所有取消操作"）
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log (action);
