# 后端运行原理说明

这份文档的目标不是只告诉你“怎么启动”，而是帮你搞懂后端在运行时到底是怎么把一个研究任务推进下去的。

## 一、后端入口

启动入口在：

- `backend/src/main/java/com/aiexplorer/researchagent/ResearchAgentApplication.java`

Spring Boot 启动后会完成这些事情：

1. 读取配置文件
2. 初始化 Web 容器（Tomcat）
3. 初始化 Spring Bean
4. 初始化数据源、JPA、Flyway 或 SQL 初始化
5. 暴露 REST 接口

如果你使用的是本地开发模式：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

那么会额外加载：

- `application-dev.yml`

这个配置会让后端：

- 使用 H2 内存数据库
- 关闭 Flyway
- 通过 `schema-dev.sql` 初始化表结构
- 暴露 H2 控制台

## 二、一次任务请求是怎么流转的

最典型的入口是创建任务接口：

- `POST /api/tasks`
- 控制器：`ResearchTaskController#createTask`

整体链路如下：

1. 前端调用 `POST /api/tasks`
2. `ResearchTaskController` 把请求交给 `ResearchTaskCommandService`
3. `ResearchTaskCommandService` 创建 `research_task`
4. 然后立即调用 `ResearchPlanningService.generateInitialPlan`
5. `ResearchPlanningService` 生成：
   - `research_plan`
   - `research_step`
   - `human_confirmation`
6. 任务状态进入 `WAITING_FOR_CONFIRMATION`
7. `TaskEventService` 记录事件日志
8. `TaskProgressStreamService` 通过 SSE 推送实时事件

也就是说，创建任务并不会立刻执行完整研究流程，而是先生成计划，再等待用户确认。

## 三、为什么要先等人工确认

这是当前项目里最重要的控制点之一。

对应代码主要在：

- `ResearchPlanningService`
- `ResearchTaskControlService`

设计原因：

- 让任务执行是“可控的”
- 避免计划不合理时直接执行
- 满足你一开始提出的“暂停 / 恢复 / 人工确认”目标

确认接口是：

- `POST /api/tasks/{taskId}/confirm`

如果用户批准：

1. 计划状态改为 `CONFIRMED`
2. 任务状态切到 `RUNNING`
3. 调用 `ResearchTaskOrchestrator.startExecution(taskId)`

如果用户拒绝：

1. 计划状态改为 `REJECTED`
2. 任务状态改为 `CANCELLED`
3. 记录用户事件并停止后续执行

## 四、真正执行任务的是谁

核心执行器是：

- `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskOrchestrator.java`

它负责的事情可以简单理解为：

- 取出任务
- 取出按顺序排好的步骤
- 一步一步执行
- 每一步都写执行记录、更新状态、写事件日志、推送 SSE
- 全部完成后生成报告并收尾

### 关键方法

#### `startExecution`

作用：

- 判断任务是同步还是异步

如果是：

- `ASYNC`：交给线程池后台执行
- `SYNC`：当前线程直接执行

异步线程池定义在：

- `AsyncExecutionConfiguration`

线程池 Bean 名称：

- `researchTaskExecutor`

#### `executeTask`

这是后端真正逐步推进任务的主循环。

它的大致逻辑是：

1. 加载任务
2. 判断任务是否应该停止
3. 补 `startedAt`
4. 读取所有步骤
5. 遍历步骤
6. 对每个步骤：
   - 更新任务当前阶段
   - 创建 `step_execution`
   - 写入 `STEP_STARTED`
   - 调用具体工具
   - 持久化来源资料
   - 完成或失败收敛
7. 所有步骤成功后，调用 `markTaskCompleted`

## 五、步骤是怎么被执行的

工具选择在：

- `ResearchToolRegistry`

当前步骤类型包括：

- `SEARCH`
- `FETCH`
- `SUMMARIZE`
- `CITATION_EXTRACT`
- `REPORT`

其中前四类走工具注册表：

- `WebSearchResearchTool`
- `PageFetchResearchTool`
- `SummarizeResearchTool`
- `CitationExtractResearchTool`

`REPORT` 这一步不走工具注册表，而是直接调用：

- `ResearchReportAssemblyService.buildReport`

## 六、事件日志和 SSE 是怎么联动的

这是这个项目读起来很关键的一部分。

### 事件日志

统一写入入口：

- `TaskEventService`

它负责：

- 记录系统事件
- 记录用户事件
- 保存到 `task_event_log`
- 推给任务的 SSE 订阅方

### SSE 推送

连接管理入口：

- `TaskProgressStreamService`

接口入口：

- `GET /api/tasks/{taskId}/stream`

核心思路是：

1. 前端为某个任务建立 `EventSource`
2. 后端把这个连接保存到 `emittersByTaskId`
3. 任务状态变化时，调用 `publish`
4. 后端向该任务所有订阅连接发送事件
5. 连接失效时自动移除

所以你在前端看到的“状态刷新”，不是轮询，而是后端主动推送。

## 七、报告是怎么生成的

报告组装服务在：

- `ResearchReportAssemblyService`

它会从两个地方取数据：

- `step_execution`：取总结和引用提取的输出
- `source_document`：取来源列表

然后组装：

- `summary`
- `keyFindings`
- `finalRecommendation`
- `reportMarkdown`
- `reportJson`

最后持久化到：

- `research_report`

## 八、查询接口是怎么组织的

查询侧拆成了几类服务：

- `ResearchTaskQueryService`：任务列表、任务详情
- `TaskActivityQueryService`：事件日志、步骤执行时间线
- `ResearchReportQueryService`：报告查询
- `TaskResponseMapper`：实体转响应对象

这样的好处是：

- 控制器很薄
- 写操作和读操作分离
- 将来更容易演进到 CQRS 风格

## 九、数据库表分别在做什么

核心表职责如下：

- `research_task`：任务主记录，保存当前状态和阶段
- `research_plan`：保存计划摘要与原始计划内容
- `research_step`：保存计划拆出来的有序步骤
- `step_execution`：保存每一步的实际执行记录
- `source_document`：保存搜索和抓取得到的来源资料
- `research_report`：保存最终报告
- `task_event_log`：保存任务级事件日志
- `human_confirmation`：保存人工确认记录

## 十、为什么测试能过但生产配置跑不起来

因为测试和运行时用的是两套配置。

测试配置：

- `src/test/resources/application.properties`
- 使用 H2 内存库
- 关闭 Flyway

默认运行配置：

- `src/main/resources/application.yml`
- 默认连接 PostgreSQL
- 默认开启 Flyway

所以在本机没有 PostgreSQL 的情况下：

- 测试能通过
- 默认启动会失败

而 `dev` profile 正是为了解决这个问题。

## 十一、建议你怎么顺着代码读

如果你是为了“真正搞懂后端怎么跑”，推荐按这个顺序调试或阅读：

1. `ResearchAgentApplication`
2. `ResearchTaskController`
3. `ResearchTaskCommandService`
4. `ResearchPlanningService`
5. `ResearchTaskControlService`
6. `ResearchTaskOrchestrator`
7. `TaskEventService`
8. `TaskProgressStreamService`
9. `ResearchReportAssemblyService`
10. `ResearchTaskQueryService` / `TaskActivityQueryService`

## 十二、当前实现的边界

当前后端已经是一个完整的 MVP，但仍有明显边界：

- 搜索 / 抓取 / 总结 / 引用提取目前还是 mock 风格实现
- Redis 依赖已接入，但还没有形成真正的缓存 / 队列 / 分布式控制方案
- 报告组装逻辑仍是 MVP 级
- 计划生成还是模板化逻辑，尚未接入真实 LLM 规划

这意味着项目已经非常适合学习“后端工程结构与运行链路”，但还没走到“生产级智能体能力完整体”。
