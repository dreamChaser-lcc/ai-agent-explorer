## Why

We want to start this project with a research-focused task execution agent that is useful as a real product and also valuable as a full-stack learning project. The first version should prioritize a clear execution workflow, controllability, and strong engineering structure over broad autonomy.

## What Changes

- Add a Java-based backend architecture for a Research Agent MVP using Spring Boot, PostgreSQL, Redis, and an LLM integration layer.
- Add a task lifecycle that supports planning, execution, reporting, and observable state transitions.
- Add two execution modes: synchronous execution for short tasks and asynchronous execution for longer-running tasks.
- Add human-in-the-loop controls so a task can pause, resume, and wait for explicit user confirmation before continuing.
- Add a research tool pipeline covering web search, page fetch, summarization, and citation extraction.
- Add report generation that turns collected sources and intermediate findings into a final research report with traceable citations.

## Capabilities

### New Capabilities
- `research-task-orchestration`: Create research tasks, generate research plans, and execute tasks through a structured lifecycle with sync and async modes.
- `human-in-the-loop-control`: Allow users to confirm plans, pause execution, and resume paused tasks.
- `research-source-processing`: Collect and process research data through web search, page fetch, summarization, and citation extraction tools.
- `research-report-generation`: Produce a final research report with summaries, findings, recommendations, and supporting citations.

### Modified Capabilities
- None.

## Impact

- Backend: new Spring Boot service structure for task orchestration, tool execution, execution control, logging, and report generation.
- Data model: new tables for tasks, plans, steps, executions, source documents, reports, event logs, and human confirmations.
- APIs: new endpoints for task creation, task detail, execution control, execution progress, and report retrieval.
- Infrastructure: PostgreSQL for persistence, Redis for execution control/state coordination, and LLM/tool provider integrations.
- Frontend: task creation, task detail timeline, confirmation actions, and report viewing flows will need to consume the new backend APIs.
