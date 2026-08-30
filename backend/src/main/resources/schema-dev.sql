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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

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
