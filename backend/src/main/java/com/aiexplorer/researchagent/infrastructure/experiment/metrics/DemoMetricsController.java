package com.aiexplorer.researchagent.infrastructure.experiment.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SB5 实验：触发自定义指标打点（访问一次 = demo.hits +1）。
 */
@RestController
@RequestMapping("/api/demo/metrics")
public class DemoMetricsController {

    private final DemoMetricsService service;

    public DemoMetricsController(DemoMetricsService service) {
        this.service = service;
    }

    @GetMapping("/hit")
    public String hit() {
        service.hit();
        return "hit";
    }
}