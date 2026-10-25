package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.application.service.RetentionCleanupService;
import com.aiexplorer.researchagent.infrastructure.cleanup.CleanupResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景⑩ 教学入口（仅 dev）：手动触发清理、查看表规模、造"过期数据"。
 * 生产环境不注册（@Profile("dev")）。
 */
@RestController
@RequestMapping("/api/admin/cleanup")
@Profile("dev")
@Tag(name = "数据清理（教学）", description = "手动清理 / 表规模 / 造过期数据；仅 dev 注册")
public class AdminCleanupController {

    private final RetentionCleanupService cleanupService;
    private final JdbcTemplate jdbcTemplate;

    public AdminCleanupController(RetentionCleanupService cleanupService, JdbcTemplate jdbcTemplate) {
        this.cleanupService = cleanupService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 手动执行一次清理（等价于定时任务触发的逻辑）。 */
    @Operation(summary = "手动执行一次清理", description = "等价于定时任务触发的逻辑")
    @PostMapping("/run")
    public CleanupResult run() {
        return cleanupService.cleanupOnce();
    }

    /** 各表当前行数（清理前后对比）。 */
    @Operation(summary = "查看各表行数", description = "用于清理前后的规模对比")
    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return cleanupService.tableStats();
    }

    /**
     * 造"过期数据"：把最近 N 个终态任务的 createdAt 改到 31 天前。
     * 仅教学用——真实场景中时间自然会流逝。
     */
    @Operation(summary = "造过期数据（教学）", description = "把最近 N 个终态任务的 createdAt 改到 31 天前")
    @PostMapping("/age")
    public Map<String, Object> age(@RequestParam(defaultValue = "3") int count) {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM research_task WHERE status IN ('COMPLETED','CANCELLED','FAILED')"
                        + " ORDER BY created_at DESC LIMIT ?", UUID.class, count);
        OffsetDateTime old = OffsetDateTime.now().minusDays(31);
        int aged = 0;
        for (UUID id : ids) {
            aged += jdbcTemplate.update("UPDATE research_task SET created_at = ? WHERE id = ?", old, id);
        }
        return Map.of("aged", aged, "taskIds", ids);
    }
}