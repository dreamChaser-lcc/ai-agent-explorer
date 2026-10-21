package com.aiexplorer.researchagent.infrastructure.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SP8 教学演示：用于验证方法级权限的临时 Controller（可删）。
 * 不污染业务 Controller——业务接口上没有标 @RequireRole。
 */
@RestController
@RequestMapping("/api/sp8")
public class Sp8DemoController {

    @GetMapping("/admin-only")
    @RequireRole("ADMIN")
    public Map<String, Object> adminOnly() {
        return Map.of("message", "管理员接口调用成功", "requiredRole", "ADMIN");
    }
}