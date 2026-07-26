# AI Agent Explorer

这是一个面向学习与工程实践的 AI Research Agent 全栈项目，目标是用一套尽量接近真实生产的结构，把“研究任务型智能体”从前端到后端完整跑通。

当前版本已经完成了一个可运行的 MVP，核心能力包括：

- 创建研究任务
- 生成研究计划并进入人工确认
- 按步骤执行搜索、抓取、总结、引用提取、报告生成
- 通过 SSE 实时推送任务进度
- 查看任务详情、执行时间线、事件日志与最终报告

## 技术栈

### 前端

- Next.js 14
- React 18
- TypeScript
- 原生 `fetch` + `EventSource`

### 后端

- Spring Boot 3.5
- Java 21
- Maven 3.9
- Spring Web
- Spring Validation
- Spring Data JPA
- Flyway
- LangChain4j
- Redis Starter（当前主要保留扩展能力）

### 数据存储

- PostgreSQL：正式运行配置默认使用
- H2：本地 `dev` 配置使用，便于快速启动和演示

## 本次会话已完成内容

- 初始化并完成 Research Agent MVP 的前后端骨架
- 实现研究任务状态机、任务控制与步骤编排
- 完成研究计划、步骤、执行记录、来源资料、报告、事件日志、人工确认等核心持久化模型
- 实现任务接口、报告接口、SSE 进度接口
- 完成前端任务创建页、任务详情页、报告页
- 完成控制器测试、编排测试、持久化测试
- 补充本地 `dev` 配置，后端可直接用 H2 运行
- 清理并补充后端中文注释与说明文档

## 已安装 / 已验证环境

本次会话中已安装或显式验证的环境如下：

- Temurin OpenJDK 21
- Apache Maven 3.9.16
- Node.js / npm：已可正常执行前端构建与开发命令

说明：

- PostgreSQL 当前未在本机安装或未启用，所以默认生产配置无法直接启动
- 为了先跑通项目，已新增 `dev` 本地配置，使用 H2 内存数据库代替 PostgreSQL

## 启动方式

### 后端

本地开发推荐直接使用 H2 的 `dev` 配置：

```powershell
cd D:\Project\ai-agent-explorer\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

启动后可访问：

- 健康检查：`http://localhost:8080/api/health`
- H2 控制台：`http://localhost:8080/h2-console`

### 前端

```powershell
cd D:\Project\ai-agent-explorer\frontend
npm run dev
```

启动后访问：

- 前端首页：`http://localhost:3000`

## 项目结构

```text
backend/
  src/main/java/com/aiexplorer/researchagent/
    api/                # 控制器、请求对象、响应对象
    application/service # 应用服务、任务编排、事件推送
    infrastructure/     # 配置、持久化、工具适配
    shared/             # 枚举、异常、共享定义
  src/main/resources/
    application.yml
    application-dev.yml
    db/migration/
    schema-dev.sql

frontend/
  app/                  # Next.js 页面
  lib/api.ts            # 前端调用后端 API 与 SSE 封装

openspec/
  changes/research-agent-mvp/
```

## 建议阅读顺序

如果你想尽快读懂后端运行流程，建议按这个顺序看：

1. `backend/src/main/java/com/aiexplorer/researchagent/ResearchAgentApplication.java`
2. `backend/src/main/java/com/aiexplorer/researchagent/api/controller/ResearchTaskController.java`
3. `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskCommandService.java`
4. `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchPlanningService.java`
5. `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskControlService.java`
6. `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskOrchestrator.java`
7. `backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskEventService.java`
8. `backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskProgressStreamService.java`
9. `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java`

## 补充文档

- `docs/会话总结-2026-07-26.md`
- `docs/后端运行链路教程.md`
- `docs/数据库表视角教程.md`
