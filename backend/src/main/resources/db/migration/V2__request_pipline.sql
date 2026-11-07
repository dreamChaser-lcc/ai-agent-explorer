-- ============================================================
-- V2：请求管线练习表（request-pipline 练习）
--   目的：给"DTO → PO(Entity) → Mapper → DB → PO → VO"这一环一个真实落点
--   表名沿用练习代码的拼写 request_pipline（与 Java 包/类保持一致）
-- ============================================================
CREATE TABLE IF NOT EXISTS request_pipline (
    id           UUID PRIMARY KEY,
    request_id   VARCHAR(64)  NOT NULL,
    request_type VARCHAR(16)  NOT NULL,
    description  VARCHAR(512) NOT NULL,
    create_time  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_time  TIMESTAMP WITH TIME ZONE NULL
);

-- 按业务键查（练习里 update 用 request_id 定位）
CREATE UNIQUE INDEX IF NOT EXISTS uk_request_pipline_request_id ON request_pipline (request_id);