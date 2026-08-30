# AI Agent Explorer

这是一个面向学习与工程实践的 AI Research Agent 全栈项目，目标是用一套尽量接近真实生产的结构，把"研究任务型智能体"从前端到后端完整跑通。

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
- Java 21+
- Maven 3.9
- Spring Web / Validation / Data JPA
- MyBatis（与 JPA 共存，可切换）
- Flyway（数据库迁移）
- LangChain4j（LLM 集成）
- Redis Starter（缓存，可选）

### 数据存储

- PostgreSQL：正式运行配置默认使用
- H2：本地 `dev` 配置使用，文件存储模式，数据持久化到 `backend/data/h2db/`

## 启动方式

如果你是在一台新电脑上第一次拉起这个项目，建议先看：

- `docs/新电脑启动准备.md`

### 后端

本地开发推荐直接使用 H2 的 `dev` 配置：

**方式一：使用启动脚本（推荐）**

```powershell
cd D:\Project\ai-agent-explorer\backend
.\start-dev.ps1
```

**方式二：手动执行命令**

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
backend/                          # 后端 Spring Boot 项目
├── pom.xml                       # Maven 配置 & 依赖声明
├── start-dev.ps1                 # Windows 启动脚本
├── .env.example                  # 环境变量模板
├── README.md                     # 后端详细文档
├── src/main/java/                # Java 源码
│   └── com/aiexplorer/researchagent/
│       ├── api/                  # API 层：控制器、请求/响应 DTO
│       ├── application/          # 应用层：业务服务、任务编排
│       ├── domain/               # 领域层（预留）
│       ├── shared/               # 共享层：枚举、异常
│       └── infrastructure/       # 基础设施层：配置、持久化、工具
├── src/main/resources/           # 配置文件
│   ├── application.yml           # 默认配置（PostgreSQL）
│   ├── application-dev.yml       # dev 环境配置（H2）
│   ├── schema-dev.sql            # H2 建表脚本
│   ├── db/migration/             # Flyway 迁移脚本
│   └── mapper/                   # MyBatis XML 映射
└── src/test/                     # 测试代码

frontend/                         # 前端 Next.js 项目
├── app/                          # 页面路由
└── lib/api.ts                    # API 调用封装

openspec/
└── changes/research-agent-mvp/   # MVP 变更记录
```

## 建议阅读顺序

如果你想尽快读懂后端运行流程，建议按这个顺序看：

1. `ResearchAgentApplication.java` — 启动入口
2. `ResearchTaskController.java` — REST 接口定义
3. `ResearchTaskCommandService.java` — 任务创建
4. `ResearchPlanningService.java` — 计划生成
5. `ResearchTaskControlService.java` — 人工确认/暂停/恢复
6. `ResearchTaskOrchestrator.java` — 核心执行引擎
7. `TaskEventService.java` — 事件记录
8. `TaskProgressStreamService.java` — SSE 实时推送

## 补充文档

- `docs/新电脑启动准备.md`
- `docs/会话总结-2026-07-26.md`
- `docs/后端运行链路教程.md`
- `docs/数据库表视角教程.md`
- `backend/README.md` — 后端项目完整文档
