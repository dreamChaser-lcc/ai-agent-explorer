## Context

This project starts from an almost empty repository and is intended to become a full-stack Research Agent system. The first version needs to be useful as a learning-oriented production-style project: it should demonstrate agent workflow orchestration, controllable execution, task visibility, and a backend architecture that is strong enough to grow.

The selected backend direction is Java with Spring Boot rather than Node.js so the project can serve as a deliberate full-stack expansion path. The first agent type is a Research Agent, which narrows the product scope to a structured workflow: generate a plan, wait for human confirmation, collect sources, process content, and produce a final report with citations.

Key constraints:
- The first version must remain small enough to build incrementally.
- The system must support both synchronous and asynchronous execution.
- The system must support pause, resume, and human confirmation.
- The tool set is intentionally limited to web search, page fetch, summarize, and citation extract.

## Goals / Non-Goals

**Goals:**
- Establish a Spring Boot backend with clear layering for API, orchestration, domain, and infrastructure concerns.
- Model research tasks as persistent, stateful workflows rather than simple chat sessions.
- Support a structured execution lifecycle: planning, confirmation, execution, and reporting.
- Provide controllable execution through sync/async modes, pause/resume, and human confirmation.
- Persist plans, steps, executions, sources, reports, and event logs in a way that supports debugging and future extension.
- Keep the MVP small enough to implement end-to-end while preserving a strong architecture.

**Non-Goals:**
- Multi-agent collaboration.
- Browser automation or general desktop automation.
- Advanced long-term memory or retrieval-augmented generation beyond stored task/source data.
- Multi-tenant enterprise access control.
- Distributed worker architecture or message-queue-first execution in the first version.
- Highly autonomous open-ended planning beyond the bounded research workflow.

## Decisions

### 1. Use Spring Boot as the backend foundation

**Decision:** Build the backend with Spring Boot, Spring Web, Spring Data JPA, PostgreSQL, Redis, and an LLM integration layer.

**Rationale:** Spring Boot provides strong engineering structure, clear layering, mature data access patterns, and a good learning path for Java backend development. PostgreSQL fits the persistent workflow and reporting data model. Redis provides lightweight runtime coordination for task control and async execution support.

**Alternatives considered:**
- **Node.js**: faster for a front-end developer, but does not achieve the intended backend learning goal.
- **Python**: stronger AI ecosystem, but weaker fit for the explicit goal of learning Java backend engineering.
- **Go**: attractive for systems programming, but less aligned with the available Java ecosystem and learning priority.

### 2. Model the product as a task execution system, not a chat system

**Decision:** The core domain will center on `ResearchTask`, `ResearchPlan`, `ResearchStep`, `StepExecution`, `SourceDocument`, `ResearchReport`, and task-level event logs.

**Rationale:** A Research Agent is closer to a workflow engine than to a plain conversation app. Separating planning from execution makes pause/resume, confirmation, retry, and reporting much clearer.

**Alternatives considered:**
- **Conversation-first model**: simpler at the beginning, but weak for long-running execution and stateful control.
- **Single-table task record with embedded JSON only**: faster initially, but poor for traceability, querying, and future extension.

### 3. Use an orchestrator-centered execution model

**Decision:** Introduce a `ResearchTaskOrchestrator` in the application layer to coordinate planning, confirmation, step execution, and report assembly.

**Rationale:** Centralizing workflow transitions in one orchestrator reduces hidden logic spread across controllers, repositories, and tool adapters. It also makes state transitions testable.

**Alternatives considered:**
- **Controller-driven orchestration**: simpler to start, but quickly becomes unmaintainable.
- **Highly framework-driven agent graph from day one**: may hide important state transitions and reduce learning value in the first version.

### 4. Support both synchronous and asynchronous execution on one workflow model

**Decision:** Use a single orchestration model and expose two execution modes:
- `SYNC` for short tasks that can complete within a request-response cycle.
- `ASYNC` for longer tasks that continue in the background after task creation.

**Rationale:** The user explicitly wants both modes. One workflow model avoids duplicating logic and keeps semantics consistent.

**Alternatives considered:**
- **Async only**: closer to production reality, but less flexible for short and demo-friendly tasks.
- **Separate sync and async pipelines**: increases implementation complexity and divergence.

### 5. Human confirmation is a first-class workflow control

**Decision:** The first version will stop after plan generation and enter `WAITING_FOR_CONFIRMATION`. Execution continues only after user approval. Pause and resume remain available while running.

**Rationale:** This is the simplest meaningful human-in-the-loop checkpoint and fits the product goal of controllable execution.

**Alternatives considered:**
- **No confirmation in v1**: simpler, but loses one of the explicit project goals.
- **Multiple confirmation gates**: more powerful, but too much complexity for the MVP.

### 6. Keep the initial tool boundary narrow and explicit

**Decision:** The first version only supports four tool capabilities:
- web search
- page fetch
- summarize
- citation extract

**Rationale:** These tools are sufficient to demonstrate the full Research Agent loop without introducing unrelated complexity.

**Alternatives considered:**
- **Add browser automation**: more agent-like, but much higher reliability and product complexity.
- **Add file system or arbitrary execution tools**: powerful, but outside the first research-focused scope.

### 7. Use structured relational storage with selective JSONB fields

**Decision:** Store stable workflow entities in relational tables and use JSONB only for variable payloads such as tool input/output, raw plan payloads, and report data.

**Rationale:** This balances queryability and flexibility. It also matches the future JPA model well.

**Alternatives considered:**
- **All relational columns**: too rigid for LLM and tool payload variability.
- **JSON-heavy schema**: easier early on, but hard to query and reason about over time.

### 8. Use LangChain4j as the initial Java-side LLM integration path

**Decision:** Integrate the model/tool layer through LangChain4j-compatible abstractions.

**Rationale:** It fits the Java ecosystem, supports LLM and tool patterns, and reduces low-level integration work while preserving room for explicit orchestration.

**Alternatives considered:**
- **Raw provider SDK calls only**: more control, but slower for MVP delivery.
- **Spring AI first**: viable, but LangChain4j is currently a more direct fit for the chosen tool-calling workflow.

### 9. Use SSE for task progress updates

**Decision:** The frontend will consume task progress through SSE rather than polling in the MVP.

**Rationale:** Task execution is stateful and user-facing. SSE provides simpler one-way real-time updates than polling while keeping the implementation lighter than a full WebSocket channel.

**Alternatives considered:**
- **Polling**: simpler to start, but adds avoidable delay and redundant requests.
- **WebSocket**: more flexible, but unnecessary for the current one-way progress update requirement.

## Risks / Trade-offs

- **Java AI tooling is less mature than Python** -> Keep the agent workflow bounded and avoid overcommitting to advanced autonomous patterns in v1.
- **Supporting sync and async modes can split logic** -> Use one orchestrator and one task state model to reduce divergence.
- **Pause/resume may be hard to implement safely during tool execution** -> Define pause as a checkpointed control between step boundaries in the first version.
- **Page fetching quality can vary across sites** -> Start with basic fetch support and accept that some pages will fail or produce partial results.
- **LLM output shape can be inconsistent** -> Use structured payload contracts and validate critical outputs before persisting transitions.
- **Too much scope can delay implementation** -> Keep v1 limited to one agent type, one confirmation checkpoint, and four tool capabilities.

## Migration Plan

1. Initialize the backend project structure and shared configuration.
2. Create the core data model and persistence layer for tasks, plans, steps, executions, sources, reports, confirmations, and event logs.
3. Implement task creation and read APIs.
4. Implement planning flow and human confirmation handling.
5. Implement tool adapters for search, fetch, summarize, and citation extraction.
6. Implement orchestrated execution for sync and async modes.
7. Implement report assembly and read APIs.
8. Add frontend flows for task creation, task detail, confirmation, and report viewing.

**Rollback strategy:** Because this is a greenfield project, rollback primarily means removing incomplete feature branches or reverting the change before release. Database migrations should be applied incrementally and be reversible where practical.

## Open Questions

- Which concrete search and page-fetch providers should be used in the first implementation?
- Should pause requests take effect only between steps, or also before selected long-running tool calls begin?
- How much raw LLM/tool payload should be persisted versus summarized for cost and storage control?
