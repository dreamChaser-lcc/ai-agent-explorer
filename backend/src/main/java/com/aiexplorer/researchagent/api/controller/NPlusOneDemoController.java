package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景⑪ 教学对照（仅 dev）：N+1 查询 vs 一条 JOIN。
 *
 * 两个接口返回【同样的数据】（任务 id + 步骤数），区别只在 SQL 条数：
 *   /n-plus-one → 1 条查任务 + N 条查步骤数（N = 本页任务数）
 *   /optimized  → 1 条 GROUP BY 搞定
 *
 * 观察方法：调接口后数日志里的 SQL 条数（Hibernate 的 binding parameter 行 / JDBC 的 delete-update 行）。
 */
@RestController
@RequestMapping("/api/sp11")
@Profile("dev")
@Tag(name = "N+1 演示（教学）", description = "对照 N+1 查询与一条 JOIN 的 SQL 条数；仅 dev 注册")
public class NPlusOneDemoController {

    private final ResearchTaskRepository taskRepository;
    private final ResearchStepRepository stepRepository;
    private final JdbcTemplate jdbcTemplate;

    public NPlusOneDemoController(ResearchTaskRepository taskRepository,
                                  ResearchStepRepository stepRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.taskRepository = taskRepository;
        this.stepRepository = stepRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** ❌ N+1 写法：1 次主查询 + 循环里 N 次子查询。 */
    @Operation(summary = "N+1 写法", description = "1 次主查询 + N 次子查询（size=5 时共 6 条 SQL）")
    @GetMapping("/tasks/n-plus-one")
    public List<Map<String, Object>> nPlusOne(@RequestParam(defaultValue = "10") int size) {
        List<ResearchTaskEntity> tasks = taskRepository
                .findAll(PageRequest.of(0, Math.min(Math.max(size, 1), 50)))
                .getContent();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResearchTaskEntity task : tasks) {
            // ★ 每个任务都发一条 SQL —— 这就是 N+1 的 N
            int stepCount = stepRepository.findByTaskIdOrderByStepNoAsc(task.getId()).size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", task.getId());
            row.put("title", task.getTitle());
            row.put("stepCount", stepCount);
            result.add(row);
        }
        return result;
    }

    /** ✅ 优化写法：一条 GROUP BY JOIN，把 N 次查询压成 1 次。 */
    @Operation(summary = "优化写法", description = "一条 LEFT JOIN + GROUP BY 搞定（1 条 SQL）")
    @GetMapping("/tasks/optimized")
    public List<Map<String, Object>> optimized(@RequestParam(defaultValue = "10") int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        return jdbcTemplate.queryForList("""
                SELECT t.id AS task_id, t.title AS title, COUNT(s.id) AS step_count
                FROM research_task t
                LEFT JOIN research_step s ON s.task_id = t.id
                GROUP BY t.id, t.title
                ORDER BY t.created_at DESC
                LIMIT ?
                """, limit);
    }
}