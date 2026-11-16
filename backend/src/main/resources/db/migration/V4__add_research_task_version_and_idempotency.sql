-- ============================================================
-- V4：补齐 research_task 表的场景③④欠账（版本列 + 幂等键 + 列表索引）
--（OpenSpec 变更 add-docker-runtime-environment 发现的历史欠账，
--  与 V3 同源：schema-dev.sql 一直在演进，Flyway 迁移漏了对应增量）
--
-- 背景：
--   ① version 列——V1 建表时就漏了（乐观锁 @Version 所需），
--      当时在 schema-dev.sql 用 ALTER 补丁解决，但没进 Flyway；
--   ② idempotency_key——场景④幂等提交加的列（同上，只改了 schema-dev.sql）；
--   ③ 幂等唯一索引——同键并发时由数据库做最终仲裁；
--   ④ 场景③列表索引——让"排序 + 分页 + 筛选"走索引（与 H2 形态对齐）。
--
-- 兼容性：全部用 IF NOT EXISTS——集群老库若已手工加过列/索引，
--        重复执行不报错（与 schema-dev.sql 的可重复执行理念一致）。
-- ============================================================

-- ① 乐观锁版本列（JPA @Version；老库补默认值 0）
ALTER TABLE research_task ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 场景④：幂等键列（客户端带来的"请求唯一标记"——重复提交在数据库层被拦下）
ALTER TABLE research_task ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

-- 场景④：幂等键唯一索引（唯一约束 = 并发仲裁者）
-- ★ 多个 NULL 不冲突：不带幂等键的老请求互不影响；同键并发时数据库作最终仲裁
CREATE UNIQUE INDEX IF NOT EXISTS uq_research_task_idempotency_key ON research_task (idempotency_key);

-- 场景③：列表查询索引（手写 SQL 的搭档——让"排序 + 分页 + 筛选"走索引）
-- idx_research_task_created_at：ORDER BY created_at DESC + LIMIT 直接走索引（免全表扫、免排序）
-- idx_research_task_status    ：WHERE status = ? 筛选走索引
CREATE INDEX IF NOT EXISTS idx_research_task_created_at ON research_task (created_at);
CREATE INDEX IF NOT EXISTS idx_research_task_status ON research_task (status);
