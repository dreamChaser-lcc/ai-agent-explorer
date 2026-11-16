-- ============================================================
-- V3：补建操作审计表 audit_log（场景⑤"第 9 张表"）
--（OpenSpec 变更 add-docker-runtime-environment 发现的历史欠账）
--
-- 背景：场景⑤当时把表加进了 H2 的 schema-dev.sql，
--      但漏了 Flyway 迁移——dev 环境靠 schema-dev.sql 建表一直没炸；
--      docker 环境首次用 Flyway 全新建库时暴露（Hibernate validate 报
--      missing table [audit_log]）。本迁移让 PG 形态与 H2 形态对齐。
-- DDL 来源：与 schema-dev.sql 中 audit_log 的定义保持一致（PG 兼容语法）。
-- ============================================================

-- 场景⑤：操作审计表（第 9 张表）——"谁、何时、干了什么"
-- 与 task_event_log 的分工：那边 = 业务事件流（任务发生了什么）；这边 = 责任审计流（谁按的按钮）
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    result VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    detail VARCHAR(512) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 审计查询主路径：按时间倒序翻最新操作（排查的第一动作）
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
-- 按操作名筛选（如"所有取消操作"）
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log (action);
