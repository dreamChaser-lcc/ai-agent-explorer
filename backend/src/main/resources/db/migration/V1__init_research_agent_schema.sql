CREATE TABLE research_task (
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

CREATE TABLE research_plan (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    version INTEGER NOT NULL,
    plan_summary TEXT NOT NULL,
    plan_objective TEXT NOT NULL,
    status VARCHAR(64) NOT NULL,
    confirmation_status VARCHAR(64) NOT NULL,
    planner_model VARCHAR(128) NULL,
    planner_prompt_snapshot TEXT NULL,
    raw_plan_payload JSONB NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE research_step (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    plan_id UUID NOT NULL REFERENCES research_plan (id),
    step_no INTEGER NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    input_payload JSONB NULL,
    expected_output TEXT NULL,
    status VARCHAR(64) NOT NULL,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    depends_on_step_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE step_execution (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    step_id UUID NOT NULL REFERENCES research_step (id),
    attempt_no INTEGER NOT NULL,
    executor_type VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128) NULL,
    status VARCHAR(64) NOT NULL,
    input_payload JSONB NULL,
    output_payload JSONB NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    duration_ms BIGINT NULL
);

CREATE TABLE source_document (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    step_id UUID NULL REFERENCES research_step (id),
    source_type VARCHAR(64) NOT NULL,
    url TEXT NULL,
    domain VARCHAR(255) NULL,
    title VARCHAR(512) NULL,
    snippet TEXT NULL,
    raw_content TEXT NULL,
    content_hash VARCHAR(128) NULL,
    language VARCHAR(32) NULL,
    fetch_status VARCHAR(64) NOT NULL,
    relevance_score DOUBLE PRECISION NULL,
    citation_ready BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE research_report (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE REFERENCES research_task (id),
    version INTEGER NOT NULL,
    summary TEXT NOT NULL,
    key_findings JSONB NULL,
    final_recommendation TEXT NULL,
    report_markdown TEXT NULL,
    report_json JSONB NULL,
    status VARCHAR(64) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE task_event_log (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    step_id UUID NULL REFERENCES research_step (id),
    event_type VARCHAR(64) NOT NULL,
    event_message TEXT NOT NULL,
    event_payload JSONB NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(128) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE human_confirmation (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES research_task (id),
    step_id UUID NULL REFERENCES research_step (id),
    confirmation_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    request_message TEXT NOT NULL,
    response_message TEXT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE NULL,
    requested_by VARCHAR(128) NULL,
    responded_by VARCHAR(128) NULL
);

CREATE INDEX idx_research_task_status_created_at ON research_task (status, created_at DESC);
CREATE INDEX idx_research_task_created_by_created_at ON research_task (created_by, created_at DESC);
CREATE INDEX idx_research_step_task_step_no ON research_step (task_id, step_no);
CREATE INDEX idx_step_execution_task_step_started_at ON step_execution (task_id, step_id, started_at DESC);
CREATE INDEX idx_source_document_task_domain ON source_document (task_id, domain);
CREATE INDEX idx_source_document_content_hash ON source_document (content_hash);
CREATE INDEX idx_task_event_log_task_created_at ON task_event_log (task_id, created_at DESC);
CREATE INDEX idx_human_confirmation_task_status ON human_confirmation (task_id, status);
