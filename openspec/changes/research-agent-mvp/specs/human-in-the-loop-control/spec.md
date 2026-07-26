## ADDED Requirements

### Requirement: User must confirm the research plan before execution continues
The system SHALL pause the workflow after plan generation and SHALL require explicit user confirmation before running research execution steps.

#### Scenario: Task waits for plan confirmation
- **WHEN** the system finishes generating a research plan
- **THEN** the task enters `WAITING_FOR_CONFIRMATION` and does not execute research steps until the user confirms

#### Scenario: Confirmed plan resumes execution
- **WHEN** the user confirms a pending research plan
- **THEN** the system transitions the task from waiting for confirmation to execution

#### Scenario: Rejected plan blocks execution
- **WHEN** the user rejects a pending research plan
- **THEN** the system does not start execution and records the rejection outcome

### Requirement: User can pause and resume a running research task
The system SHALL allow a user to pause a running task and later resume it from the persisted workflow state.

#### Scenario: Pause running task
- **WHEN** the user sends a pause request for a running task
- **THEN** the system marks the task as paused and prevents new execution steps from starting

#### Scenario: Resume paused task
- **WHEN** the user sends a resume request for a paused task
- **THEN** the system continues execution from the next valid step checkpoint

### Requirement: Human control actions are auditable
The system SHALL persist confirmation, pause, and resume actions as task-level events.

#### Scenario: Confirmation action is logged
- **WHEN** a user confirms or rejects a research plan
- **THEN** the system records the action with its timestamp and outcome

#### Scenario: Pause or resume action is logged
- **WHEN** a user pauses or resumes a research task
- **THEN** the system records the control action in the task event history
