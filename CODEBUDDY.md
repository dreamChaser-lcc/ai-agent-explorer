# CODEBUDDY.md

此文件为 CodeBuddy 提供项目上下文指引，AI 每次对话都会自动读取。

## 项目概述

AI Agent Explorer — 一个面向学习与工程实践的 AI Research Agent 全栈项目。MVP 已实现完整的研究任务流程：创建任务 → 生成研究计划并人工确认 → 按步骤执行（搜索、抓取、总结、引用提取、报告生成）→ 通过 SSE 实时推送进度 → 查看任务详情、时间线、事件日志与最终报告。

## 技术栈

### 前端
- Next.js 14（App Router）
- React 18
- TypeScript 5.6
- 原生 `fetch` + `EventSource`（未使用 axios）

### 后端
- Spring Boot 3.5
- Java 21
- Maven 3.9
- Spring Data JPA + Flyway
- LangChain4j
- H2（开发环境）/ PostgreSQL（生产环境）

## 项目结构

```
backend/src/main/java/com/aiexplorer/researchagent/
  api/                # 控制器、请求/响应 DTO
  application/service # 应用服务、任务编排、事件推送
  infrastructure/     # 配置、持久化、工具适配
  shared/             # 枚举、异常、共享定义

frontend/
  app/                # Next.js 页面
  lib/api.ts          # 前端 API 调用与 SSE 封装
```

## 常用命令

### 后端（dev 模式，使用 H2 内存数据库）
```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 前端
```powershell
cd frontend
npm install
npm run dev
```

## 编码约定

- **语言**：代码注释和文档统一使用中文。
- **CSS 规范**：不使用行内样式。避免 `gap` 属性（webview 兼容性差）。优先复用已有样式类，没有则新建 class。
- **命名规范**：函数和变量使用多词大驼峰命名，避免单字母或单一单词，命名需语义明确。
- **注释保护**：未经明确确认，不得删除已有注释或文档内容。
- **组件化**：代码编写需具备组件化、模块化思想。

## 架构说明

- 后端采用类 DDD 分层架构：api → application → infrastructure → shared
- 任务编排流程：`ResearchTaskController` → `ResearchTaskCommandService` → `ResearchTaskOrchestrator` → `ResearchPlanningService` / `ResearchTaskControlService`
- SSE 进度推送：`TaskProgressStreamService`
- 事件日志：`TaskEventService`
- 报告生成：`ResearchReportAssemblyService`
- dev 配置（`application-dev.yml`）使用 H2 内存数据库（PostgreSQL 兼容模式），无需安装 PostgreSQL 即可本地开发
- dev 模式下 Flyway 禁用，通过 `schema-dev.sql` 初始化表结构

## OpenSpec

本项目使用 OpenSpec 进行规范驱动开发，可用斜杠命令：
- `/opsx:propose` — 提出新变更
- `/opsx:apply` — 实施变更
- `/opsx:archive` — 归档已完成变更
- `/opsx:explore` — 探索/梳理思路
- `/opsx:sync` — 同步 delta spec 到主 spec
- `/opsx:update` — 更新变更规划
