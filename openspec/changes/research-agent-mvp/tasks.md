## 1. Project Setup

- [x] 1.1 Initialize a Spring Boot backend project with Maven and baseline dependencies for Web, Validation, JPA, PostgreSQL, Redis, and LangChain4j
- [x] 1.2 Create the backend package structure for api, application, domain, infrastructure, and shared modules
- [x] 1.3 Add environment-based configuration for database, Redis, and LLM provider settings
- [x] 1.4 Create the initial frontend app shell for task creation, task detail, and report viewing pages

## 2. Persistence and Domain Model

- [x] 2.1 Create database migrations for research tasks, plans, steps, step executions, source documents, reports, event logs, and human confirmations
- [x] 2.2 Implement JPA entities, repositories, and enums for the core research workflow model
- [x] 2.3 Add DTOs and mapping logic for task summaries, task details, execution records, and reports
- [x] 2.4 Add persistence tests for the core entities and repository queries

## 3. Task Lifecycle and Orchestration

- [x] 3.1 Implement task creation and task query application services
- [x] 3.2 Implement research plan generation and persistence through a planner workflow
- [x] 3.3 Implement the `WAITING_FOR_CONFIRMATION` checkpoint after plan generation
- [x] 3.4 Implement the `ResearchTaskOrchestrator` to run ordered research steps and update task state transitions
- [x] 3.5 Implement pause, resume, and cancel control handling with persisted event logs
- [x] 3.6 Implement sync and async execution modes on the same orchestration flow

## 4. Research Tool Pipeline

- [x] 4.1 Implement a tool registry abstraction for web search, page fetch, summarize, and citation extract capabilities
- [x] 4.2 Integrate a web search adapter and persist search results as source candidates
- [x] 4.3 Integrate a page fetch adapter and persist fetched page content and fetch status
- [x] 4.4 Implement summarization and citation extraction steps with validated structured outputs
- [x] 4.5 Persist step execution inputs, outputs, durations, and failures for debugging and timeline display

## 5. Reporting and API Surface

- [x] 5.1 Implement report assembly from summarized findings and citation-ready sources
- [x] 5.2 Implement REST APIs for task creation, task list, task detail, execution control, and report retrieval
- [x] 5.3 Implement an API for task event history and step execution timeline data
- [x] 5.4 Add controller and orchestration tests for task lifecycle, confirmation, pause/resume, and report generation

## 6. Frontend Workflow

- [x] 6.1 Build the task creation flow with research goal input and sync/async mode selection
- [x] 6.2 Build the task detail page with task status, plan summary, step timeline, and control actions
- [x] 6.3 Build the human confirmation UI for approving or rejecting generated plans
- [x] 6.4 Build the report page with summary, findings, recommendation, and source references
- [x] 6.5 Connect frontend SSE progress updates to the backend task lifecycle APIs
