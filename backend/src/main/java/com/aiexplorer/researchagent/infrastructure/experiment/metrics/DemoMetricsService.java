package com.aiexplorer.researchagent.infrastructure.experiment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * SB5 实验：自定义指标（Counter + Gauge）。
 *
 * <p>访问 /api/demo/metrics/hit 触发计数；用 /actuator/metrics/demo.hits 查看。</p>
 */
@Service
public class DemoMetricsService {

    private final Counter hits;
    private final AtomicInteger active = new AtomicInteger();

    public DemoMetricsService(MeterRegistry registry) {
        this.hits = Counter.builder("demo.hits")
                .description("Demo 接口被调用次数")
                .tag("module", "experiment")
                .register(registry);
        // Gauge 绑定到 AtomicInteger（当前活跃数示例）
        registry.gauge("demo.active", active);
    }

    public void hit() {
        hits.increment();
        active.incrementAndGet();
    }
}