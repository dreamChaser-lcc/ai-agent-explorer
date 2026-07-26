## ADDED Requirements

### Requirement: User can create a research task
The system SHALL allow a user to create a research task by submitting a research goal and an execution mode. The system SHALL persist the created task and prepare it for planning.

#### Scenario: Create asynchronous research task
- **WHEN** the user submits a research goal with execution mode `ASYNC`
- **THEN** the system creates a persisted task record and marks it ready to enter the planning workflow

#### Scenario: Create synchronous research task
- **WHEN** the user submits a research goal with execution mode `SYNC`
- **THEN** the system creates a persisted task record and starts the same planning workflow using the synchronous execution path

### Requirement: Research task follows a structured lifecycle
The system SHALL move each research task through explicit lifecycle states for planning, confirmation, execution, completion, failure, pause, and cancellation.

#### Scenario: Task enters planning after creation
- **WHEN** a new task is accepted by the system
- **THEN** the task enters a planning state before any research execution begins

#### Scenario: Task completes after successful execution
- **WHEN** all planned research steps and report generation succeed
- **THEN** the system marks the task as completed

#### Scenario: Task fails on unrecoverable execution error
- **WHEN** an unrecoverable execution error occurs during planning, execution, or reporting
- **THEN** the system marks the task as failed and stores failure details for inspection

### Requirement: Research task execution is step-oriented
The system SHALL represent research work as ordered steps so execution progress can be tracked and resumed.

#### Scenario: Planned steps are persisted
- **WHEN** the planning stage generates a research plan
- **THEN** the system stores ordered research steps associated with the task

#### Scenario: Current step is observable
- **WHEN** a task is in execution
- **THEN** the system exposes which step is currently pending, running, completed, or failed
