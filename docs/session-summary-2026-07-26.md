# 会话总结（2026-07-26）

## 目标

本次连续会话围绕“从零搭建一个可运行的 AI Research Agent 全栈项目”展开，目标是先完成一个可落地、可演示、可继续扩展的 MVP。

## 已完成的核心工作

### 1. 项目定位与架构收敛

- 明确产品方向为任务执行型智能体
- 确定第一版聚焦 `Research Agent`
- 明确技术路线：
  - 前端：Next.js
  - 后端：Spring Boot + Java 21
  - 数据库：PostgreSQL
  - 缓存 / 扩展：Redis
  - AI 集成：LangChain4j
- 明确交互要求：
  - 支持同步 / 异步执行
  - 支持暂停 / 恢复
  - 支持人工确认
  - 前端进度使用 SSE

### 2. 后端 MVP 实现

已完成后端以下能力：

- 研究任务创建
- 研究计划生成
- 进入 `WAITING_FOR_CONFIRMATION`
- 用户确认 / 拒绝计划
- 步骤编排执行：
  - SEARCH
  - FETCH
  - SUMMARIZE
  - CITATION_EXTRACT
  - REPORT
- 任务暂停 / 恢复 / 取消
- 执行日志记录
- SSE 实时推送
- 研究报告组装与查询

### 3. 持久化模型与数据库

已实现并落地以下表对应的实体 / 仓储 / 迁移：

- `research_task`
- `research_plan`
- `research_step`
- `step_execution`
- `source_document`
- `research_report`
- `task_event_log`
- `human_confirmation`

### 4. 前端 MVP 实现

已完成以下页面与交互：

- 任务创建页
- 最近任务列表
- 任务详情页
- 计划确认 / 暂停 / 恢复 / 取消操作
- 执行时间线与事件日志展示
- 报告查看页
- SSE 实时刷新

### 5. 测试与验证

已完成：

- 控制器测试
- 编排服务测试
- 仓储持久化测试

并已验证：

- 后端测试全部通过
- 前端构建成功
- 后端 `dev` 配置可启动
- 前端开发服务可启动

## 本次会话中安装 / 处理的环境

### 已安装或明确补齐

- Temurin OpenJDK 21
- Apache Maven 3.9.16

### 已使用并验证

- Node.js / npm

### 当前本地数据库情况

- 本机未检测到可直接使用的 PostgreSQL 服务
- 因此新增了 `dev` 本地配置，使用 H2 内存数据库先跑通项目

## 本次新增的重要文档与能力

- 新增项目总说明：`README.md`
- 新增本次会话总结：`docs/session-summary-2026-07-26.md`
- 新增后端运行原理说明：`docs/backend-runtime-guide.md`
- 新增后端本地开发配置：`backend/src/main/resources/application-dev.yml`
- 新增后端本地建表脚本：`backend/src/main/resources/schema-dev.sql`

## 当前推荐的运行方式

### 后端

```powershell
cd D:\Project\ai-agent-explorer\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 前端

```powershell
cd D:\Project\ai-agent-explorer\frontend
npm run dev
```

## 当前访问地址

- 前端首页：`http://localhost:3000`
- 后端健康检查：`http://localhost:8080/api/health`
- H2 控制台：`http://localhost:8080/h2-console`

## 当前项目状态

项目已经从“架构探索阶段”推进到“可运行 MVP 阶段”。  
目前最适合继续推进的方向有：

- 接入真实搜索 provider
- 接入真实页面抓取 provider
- 引入更真实的 LLM 输出约束与错误恢复
- 完善前后端联调与示例数据体验
- 再切回 PostgreSQL / Redis 的本地完整环境
