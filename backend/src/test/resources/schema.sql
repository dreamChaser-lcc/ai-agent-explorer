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

CREATE TABLE research_step (
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

CREATE TABLE source_document (
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

CREATE TABLE research_report (
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
