package com.aiexplorer.researchagent.infrastructure.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

/**
 * SP8 教学演示：用于验证方法级权限的临时 Controller（可删）。
 * 不污染业务 Controller——业务接口上没有标 @RequireRole。
 */
@RestController
@RequestMapping("/api/sp8")
@Tag(name = "拦截器演示（教学）", description = "SP8 实验：方法级 @RequireRole 权限校验") class Sp8DemoController {

    @Operation(summary = "仅 ADMIN 可访问", description = "由 PermissionInterceptor 读取方法注解后校验角色")
    @GetMapping("/admin-only")
    @RequireRole("ADMIN")
    public Map<String, Object> adminOnly() {
        return Map.of("message", "管理员接口调用成功", "requiredRole", "ADMIN");
    }
}