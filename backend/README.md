# Research Agent Backend

Spring Boot 后端服务，提供研究任务的创建、执行、报告生成等 REST API。

## 快速启动

```powershell
# 方式一：启动脚本
.\start-dev.ps1

# 方式二：手动命令
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

启动后访问：
- 健康检查：`http://localhost:8080/api/health`
- H2 控制台：`http://localhost:8080/h2-console`

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.13 | 应用框架 |
| Java | 21+ | 运行时 |
| Maven | 3.9+ | 构建工具 |
| Spring Data JPA | - | ORM 持久化（默认） |
| MyBatis | 3.0.5 | ORM 持久化（可选） |
| H2 | 2.3.232 | 开发用嵌入式数据库 |
| PostgreSQL | - | 生产数据库 |
| Redis | - | 缓存（可选） |
| LangChain4j | 1.17.2-beta27 | LLM 集成（预留） |
| Flyway | - | 数据库版本迁移 |

## 项目结构

```text
backend/
├── pom.xml                       # Maven 项目配置
├── start-dev.ps1                 # Windows 启动脚本
├── .env.example                  # 环境变量模板
├── data/h2db/                    # H2 数据库文件（dev 环境）
│
├── src/main/java/com/aiexplorer/researchagent/
│   ├── ResearchAgentApplication.java          # 启动入口
│   │
│   ├── api/                                    # ── API 层 ──
│   │   ├── controller/                         #   REST 控制器
│   │   ├── request/                            #   请求 DTO
│   │   └── response/                           #   响应 DTO
│   │
│   ├── application/                            # ── 应用层 ──
│   │   └── service/                            #   业务服务
│   │
│   ├── domain/                                 # ── 领域层（预留）──
│   │
│   ├── shared/                                 # ── 共享层 ──
│   │   ├── enums/                              #   业务枚举
│   │   └── exception/                          #   自定义异常
│   │
│   └── infrastructure/                         # ── 基础设施层 ──
│       ├── config/                             #   配置类
│       ├── tools/                              #   研究工具适配器
│       └── persistence/                        #   持久化
│           ├── entity/                         #     JPA 实体
│           ├── repository/                     #     Spring Data 仓库
│           └── store/                          #     双实现存储（JPA/MyBatis）
│
├── src/main/resources/
│   ├── application.yml                         # 默认配置
│   ├── application-dev.yml                     # dev 环境配置
│   ├── schema-dev.sql                          # H2 建表脚本
│   ├── db/migration/V1__init_*.sql             # Flyway 迁移
│   └── mapper/TaskEventLogMapper.xml           # MyBatis SQL 映射
│
└── src/test/                                   # 测试代码
    ├── java/.../controller/                    #   控制器测试
    ├── java/.../service/                       #   服务测试
    ├── java/.../repository/                    #   持久化测试
    └── resources/application.properties        #   测试配置
```

## 架构分层

项目采用 **4 层架构**，依赖方向从上到下：

```
┌──────────────────────────────────────┐
│           API 层 (api/)              │
│  控制器 + 请求/响应 DTO              │
│  职责：接收 HTTP 请求，返回 JSON      │
└──────────────────┬───────────────────┘
                   │
┌──────────────────▼───────────────────┐
│        应用层 (application/)          │
│  服务 + 编排器 + SSE 推送            │
│  职责：业务逻辑、流程编排、事件广播   │
└──────────────────┬───────────────────┘
                   │
┌──────────────────▼───────────────────┐
│       基础设施层 (infrastructure/)    │
│  配置 | 工具 | 持久化 | 外部集成     │
│  职责：数据库访问、LLM 调用、缓存     │
└──────────────────┬───────────────────┘
                   │
┌──────────────────▼───────────────────┐
│          共享层 (shared/)             │
│  枚举 | 异常 | 常量                  │
│  职责：跨层共享的定义                 │
└──────────────────────────────────────┘
```

## 文件详细说明

### 启动入口

| 文件 | 说明 |
|------|------|
| `ResearchAgentApplication.java` | Spring Boot 启动类，包含 `main()` 方法 |

### API 层 (`api/`)

| 文件 | 说明 |
|------|------|
| `controller/ResearchTaskController.java` | 任务 REST 接口：创建、列表、详情、执行时间线、事件日志、SSE 流、确认、暂停、恢复、取消 |
| `controller/ResearchReportController.java` | 报告 REST 接口：按 taskId 查询研究报告 |
| `controller/HealthController.java` | 健康检查 `/api/health` |
| `controller/ApiExceptionHandler.java` | 全局异常处理，统一错误 JSON 格式 |
| `request/CreateResearchTaskRequest.java` | 创建任务请求：title、goal、executionMode |
| `request/PlanConfirmationRequest.java` | 计划确认请求：approved、responseMessage |
| `response/TaskSummaryResponse.java` | 任务列表项 DTO |
| `response/TaskDetailResponse.java` | 任务详情 DTO（含计划和步骤） |
| `response/TaskEventResponse.java` | 事件日志项 DTO |
| `response/StepExecutionResponse.java` | 执行时间线项 DTO |
| `response/ResearchReportResponse.java` | 研究报告 DTO |

### 应用层 (`application/service/`)

| 文件 | 说明 |
|------|------|
| `ResearchTaskOrchestrator.java` | **核心执行引擎**（485 行）。驱动步骤流水线：遍历有序步骤 → 调用工具 → 持久化结果 → 管理状态 → 广播 SSE |
| `ResearchTaskCommandService.java` | 写服务：创建任务 + 触发计划生成 |
| `ResearchTaskQueryService.java` | 读服务：任务列表/详情（支持 Redis 缓存） |
| `ResearchTaskControlService.java` | 控制服务：确认计划、暂停、恢复、取消 |
| `ResearchPlanningService.java` | 计划生成：创建 5 步研究计划（SEARCH→FETCH→SUMMARIZE→CITATION_EXTRACT→REPORT） |
| `ResearchReportAssemblyService.java` | 报告组装：从步骤执行结果中提取摘要和引用，生成最终报告 |
| `ResearchReportQueryService.java` | 读服务：查询研究报告 |
| `TaskActivityQueryService.java` | 读服务：查询执行时间线和事件日志 |
| `TaskEventService.java` | 事件服务：记录事件 + 广播 SSE |
| `TaskProgressStreamService.java` | SSE 连接管理：订阅/发布/生命周期管理 |
| `TaskResponseMapper.java` | 实体 → DTO 转换器 |
| `ResearchToolRegistry.java` | **策略模式注册表**：StepType → ResearchTool 的映射，自动发现新工具 |

### 基础设施层 — 配置 (`infrastructure/config/`)

| 文件 | 说明 |
|------|------|
| `PropertiesConfiguration.java` | 启用 `@ConfigurationProperties` 绑定 |
| `ExecutionProperties.java` | `app.execution.*` 配置绑定：执行模式、线程池大小等 |
| `LlmProperties.java` | `app.llm.*` 配置绑定：LLM 提供商、模型、温度等 |
| `AsyncExecutionConfiguration.java` | 异步执行线程池 Bean 配置 |
| `RedisCacheConfig.java` | Redis 缓存配置（30s TTL） |
| `WebCorsConfiguration.java` | CORS 跨域配置（允许 localhost:3000） |

### 基础设施层 — 研究工具 (`infrastructure/tools/`)

| 文件 | 说明 |
|------|------|
| `ResearchTool.java` | 工具接口：`getSupportedStepType()` + `execute()` |
| `ResearchToolContext.java` | 工具输入：任务、步骤、已有来源文档 |
| `ResearchToolResult.java` | 工具输出：输出 JSON + 来源文档列表 |
| `ToolSourceDocument.java` | 工具产生的来源文档记录 |
| `WebSearchResearchTool.java` | **搜索工具**（Mock 实现） |
| `PageFetchResearchTool.java` | **抓取工具**（Mock 实现） |
| `SummarizeResearchTool.java` | **总结工具**（Mock 实现） |
| `CitationExtractResearchTool.java` | **引用提取工具**（Mock 实现） |

> 当前所有工具均为 Mock 实现，LangChain4j 集成已预留但未接入真实 LLM。

### 基础设施层 — 持久化 (`infrastructure/persistence/`)

#### JPA 实体 (`entity/`)

| 文件 | 对应表 | 说明 |
|------|--------|------|
| `BaseTimeEntity.java` | - | 基类：自动管理 createdAt/updatedAt |
| `ResearchTaskEntity.java` | `research_task` | 研究任务主表 |
| `ResearchPlanEntity.java` | `research_plan` | 研究计划（版本化） |
| `ResearchStepEntity.java` | `research_step` | 研究步骤（有序） |
| `StepExecutionEntity.java` | `step_execution` | 步骤执行记录 |
| `SourceDocumentEntity.java` | `source_document` | 来源文档 |
| `ResearchReportEntity.java` | `research_report` | 研究报告（每任务一份） |
| `TaskEventLogEntity.java` | `task_event_log` | 事件日志（审计） |
| `HumanConfirmationEntity.java` | `human_confirmation` | 人工确认记录 |

#### JPA 仓库 (`repository/`)

| 文件 | 说明 |
|------|------|
| `ResearchTaskRepository.java` | 任务 CRUD |
| `ResearchPlanRepository.java` | + `findByTaskIdOrderByVersionDesc()` |
| `ResearchStepRepository.java` | + `findByTaskIdOrderByStepNoAsc()` |
| `StepExecutionRepository.java` | + `findByTaskIdOrderByStartedAtDesc()` |
| `SourceDocumentRepository.java` | + `findByTaskIdOrderByCreatedAtDesc()` |
| `ResearchReportRepository.java` | + `findByTaskId()` |
| `TaskEventLogRepository.java` | + `findByTaskIdOrderByCreatedAtDesc()` |
| `HumanConfirmationRepository.java` | + `findByTaskIdOrderByRequestedAtDesc()` |

#### 双实现存储 (`store/`)

| 文件 | 说明 |
|------|------|
| `TaskEventLogStore.java` | 策略接口，定义 `save()` 和 `findByTaskId()` |
| `JpaTaskEventLogStore.java` | JPA 实现（`app.persistence.mode=jpa` 时激活） |
| `MyBatisTaskEventLogStore.java` | MyBatis 实现（`app.persistence.mode=mybatis` 时激活） |
| `TaskEventLogMapper.java` | MyBatis Mapper 接口 |
| `UuidTypeHandler.java` | MyBatis UUID 类型处理器 |
| `JsonNodeTypeHandler.java` | MyBatis JsonNode 类型处理器 |
| `PersistenceModeDemoRunner.java` | 启动时演示当前持久化模式 |

### 共享层 (`shared/`)

#### 枚举 (`enums/`)

| 枚举 | 值 | 说明 |
|------|-----|------|
| `TaskStatus` | DRAFT, QUEUED, PLANNING, WAITING_FOR_CONFIRMATION, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED | 任务状态 |
| `TaskStage` | PLANNING, EXECUTING, REPORTING | 任务阶段 |
| `StepType` | SEARCH, FETCH, SUMMARIZE, CITATION_EXTRACT, REPORT | 步骤类型 |
| `StepStatus` | PENDING, READY, WAITING_CONFIRMATION, COMPLETED, FAILED, SKIPPED | 步骤状态 |
| `ExecutionMode` | SYNC, ASYNC | 执行模式 |
| `EventType` | TASK_CREATED, PLAN_GENERATED, ... 等 11 个值 | 事件类型 |
| `PlanStatus` | DRAFT, GENERATED, CONFIRMED, REJECTED | 计划状态 |
| `ReportStatus` | DRAFT, GENERATED, FINAL | 报告状态 |

#### 异常 (`exception/`)

| 文件 | 说明 |
|------|------|
| `TaskNotFoundException.java` | 任务不存在异常（返回 404） |

### 配置文件 (`resources/`)

| 文件 | 说明 |
|------|------|
| `application.yml` | 默认配置：PostgreSQL、Redis、JPA、Flyway、MyBatis、日志、LLM |
| `application-dev.yml` | dev 覆盖：H2 文件数据库、SQL 初始化、H2 Console |
| `schema-dev.sql` | H2 建表脚本（8 张表，`CREATE TABLE IF NOT EXISTS`） |
| `db/migration/V1__init_research_agent_schema.sql` | Flyway 生产迁移（8 张表 + 索引） |
| `mapper/TaskEventLogMapper.xml` | MyBatis SQL：insert/findById/findByTaskId |

### 测试 (`src/test/`)

| 文件 | 说明 |
|------|------|
| `ResearchTaskControllerTest.java` | `@WebMvcTest` 控制器单元测试 |
| `ResearchTaskOrchestratorTest.java` | 编排器单元测试（Mockito） |
| `RepositoryPersistenceTest.java` | `@DataJpaTest` 持久化集成测试 |
| `JpaTaskEventLogStoreIntegrationTest.java` | JPA 模式集成测试 |
| `MyBatisTaskEventLogStoreIntegrationTest.java` | MyBatis 模式集成测试 |

## 数据库表结构

```
research_task ─────┬── research_plan ──── research_step ──── step_execution
                   │                                        ─── source_document
                   ├── research_report
                   ├── task_event_log
                   └── human_confirmation
```

| 表 | 说明 |
|----|------|
| `research_task` | 研究任务主表：标题、目标、状态、执行模式 |
| `research_plan` | 研究计划（版本化）：计划摘要、确认状态 |
| `research_step` | 研究步骤：类型（SEARCH/FETCH/SUMMARIZE/CITATION_EXTRACT/REPORT）、状态 |
| `step_execution` | 步骤执行记录：输入/输出 JSON、耗时、错误信息 |
| `source_document` | 来源文档：URL、内容、相关性评分、引用状态 |
| `research_report` | 研究报告：摘要、关键发现、Markdown 报告 |
| `task_event_log` | 事件日志：审计追踪，记录所有状态变更 |
| `human_confirmation` | 人工确认：计划/步骤审批记录 |

## 任务状态流转

```
DRAFT → QUEUED → PLANNING → WAITING_FOR_CONFIRMATION → RUNNING → COMPLETED
                                  │                          │
                                  ├─ reject → CANCELLED      ├─ pause → PAUSED
                                  │                          │
                                  │                          └─ fail → FAILED
```

## 步骤执行流程

```
SEARCH → FETCH → SUMMARIZE → CITATION_EXTRACT → REPORT
   │        │        │              │                │
   ▼        ▼        ▼              ▼                ▼
 搜索结果  抓取页面  总结内容     提取引用         组装报告
 (Mock)   (Mock)   (Mock)       (Mock)         (Mock)
```

## 关键设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| 策略模式 | `ResearchToolRegistry` + `ResearchTool` | 每个工具实现相同接口，按 StepType 自动分发 |
| 策略模式 | `TaskEventLogStore`（JPA/MyBatis 双实现） | 通过配置切换持久化实现 |
| 编排器模式 | `ResearchTaskOrchestrator` | 驱动多步骤流水线，管理状态和事件 |
| 观察者模式 | `TaskEventService` + SSE | 状态变更同时持久化和广播 |
| CQRS | Command/Query 分离服务 | 读写职责分离 |
| 缓存旁路 | `@Cacheable` on 查询服务 | Redis 缓存高频读请求 |

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | - | 激活的 profile（如 `dev`） |
| `SERVER_PORT` | 8080 | 服务端口 |
| `DB_URL` | PostgreSQL 默认地址 | 数据库连接 URL |
| `DB_USERNAME` | postgres | 数据库用户名 |
| `DB_PASSWORD` | postgres | 数据库密码 |
| `REDIS_HOST` | localhost | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `APP_PERSISTENCE_MODE` | jpa | 持久化模式：jpa / mybatis |
| `APP_EXECUTION_MODE` | ASYNC | 执行模式：SYNC / ASYNC |
| `APP_LLM_PROVIDER` | openai | LLM 提供商 |
| `APP_LLM_CHAT_MODEL` | gpt-4o-mini | LLM 模型 |
| `OPENAI_API_KEY` | - | OpenAI API 密钥 |

## 配置切换指南

### 切换持久化模式（JPA ↔ MyBatis）

修改 `application.yml`：
```yaml
app:
  persistence:
    mode: mybatis   # 或 jpa
```

### 切换数据库（H2 ↔ PostgreSQL）

启动时指定 profile：
```powershell
# H2（dev 环境）
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# PostgreSQL（默认 profile）
mvn spring-boot:run
```

### 切换执行模式（同步 ↔ 异步）

修改 `application.yml`：
```yaml
app:
  execution:
    mode: SYNC   # 或 ASYNC
```
