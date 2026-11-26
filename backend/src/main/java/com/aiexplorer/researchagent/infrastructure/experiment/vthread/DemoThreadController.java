package com.aiexplorer.researchagent.infrastructure.experiment.vthread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SB7 实验：观察请求处理线程（开/关虚拟线程对比）。
 *
 * <p>访问 GET /api/demo/thread 返回当前线程信息：</p>
 * <pre>
 *   平台线程（默认）：Thread[#45,http-nio-8080-exec-1,5,main] isVirtual=false
 *   虚拟线程（spring.threads.virtual.enabled=true）：Thread[#xx,VirtualThread[#..],..] isVirtual=true
 * </pre>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoThreadController {

    @GetMapping("/thread")
    public String thread() {
        Thread t = Thread.currentThread();
        return "name=" + t.getName() + " | isVirtual=" + t.isVirtual() + " | class=" + t.getClass().getSimpleName();
    }
}