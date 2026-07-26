package com.aiexplorer.researchagent.api.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供基础健康检查接口，用于确认后端能够正常启动。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 返回最基础的服务存活状态，用于联调和启动验证。
     */
    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "research-agent-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
