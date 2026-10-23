package com.aiexplorer.researchagent.infrastructure.cleanup;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 场景⑩：清理用的批量 JDBC 访问（不用 JPA）。
 *
 * 为什么不用 JPA 逐条删：
 *   清理是"按条件批量删除"，JPA 的 deleteBy 会先查实体再逐条 DELETE ——
 *   一万行就是一万条 SQL（外加实体加载）；JdbcTemplate 一条 DELETE 批量搞定。
 *
 * 安全要点：**表名不能参数化**——只能用白名单常量拼接，绝不能传入外部字符串。
 */
@Repository
public class RetentionCleanupDao {

    /** 允许清理的子表白名单（顺序：先子后父，避免外键/引用残留）。 */
    private static final List<String> CHILD_TABLES = List.of(
            "step_execution",
            "research_step",
            "source_document",
            "research_plan",
            "research_report",
            "human_confirmation",
            "task_event_log");

    private final JdbcTemplate jdbcTemplate;

    public RetentionCleanupDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查过期任务 ID（只查 ID，不加载实体——清理不需要实体）。 */
    public List<UUID> findExpiredTaskIds(Collection<String> statuses, OffsetDateTime cutoff, int limit) {
        String placeholders = statuses.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id FROM research_task WHERE status IN (" + placeholders + ")"
                + " AND created_at < ? ORDER BY created_at ASC LIMIT ?";
        Object[] args = new Object[statuses.size() + 2];
        int index = 0;
        for (String status : statuses) {
            args[index++] = status;
        }
        args[index++] = cutoff;
        args[index] = limit;
        return jdbcTemplate.queryForList(sql, UUID.class, args);
    }

    /** 按任务 ID 删除全部子表数据（分批 IN，返回删除行数）。 */
    public int deleteChildrenByTaskIds(List<UUID> taskIds, int batchSize) {
        int total = 0;
        for (List<UUID> batch : partition(taskIds, batchSize)) {
            String in = batch.stream().map(id -> "?").collect(Collectors.joining(","));
            for (String table : CHILD_TABLES) {
                total += jdbcTemplate.update(
                        "DELETE FROM " + table + " WHERE task_id IN (" + in + ")", batch.toArray());
            }
        }
        return total;
    }

    /** 删除任务主表行。 */
    public int deleteTasksByIds(List<UUID> taskIds, int batchSize) {
        int total = 0;
        for (List<UUID> batch : partition(taskIds, batchSize)) {
            String in = batch.stream().map(id -> "?").collect(Collectors.joining(","));
            total += jdbcTemplate.update("DELETE FROM research_task WHERE id IN (" + in + ")", batch.toArray());
        }
        return total;
    }

    /** 清理"无主"的旧事件（所属任务已不存在）+ 超出保留期的旧事件。 */
    public int deleteOldEvents(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.update(
                "DELETE FROM task_event_log WHERE created_at < ? LIMIT ?", cutoff, limit);
    }

    /** 清理过期审计日志。 */
    public int deleteOldAuditLogs(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.update("DELETE FROM audit_log WHERE created_at < ? LIMIT ?", cutoff, limit);
    }

    /** 统计表行数（教学：清理前后对比）。表名走白名单。 */
    public int countOf(String table) {
        if (!"research_task".equals(table) && !CHILD_TABLES.contains(table) && !"audit_log".equals(table)) {
            throw new IllegalArgumentException("非法表名：" + table);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private List<List<UUID>> partition(List<UUID> source, int size) {
        List<List<UUID>> batches = new ArrayList<>();
        for (int start = 0; start < source.size(); start += size) {
            batches.add(source.subList(start, Math.min(start + size, source.size())));
        }
        return batches;
    }
}