package com.aiexplorer.researchagent.infrastructure.experiment.starter;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SB6 实验：切片测试（@WebMvcTest）——只加载 Web 层，Service 用 Mock 替身。
 *
 * <p>关键点：</p>
 * <pre>
 *   ① controllers = DemoGreetingController.class  → 只装配这一个 Controller（不扫全项目）
 *   ② @MockitoBean（Boot 3.4+ 替代 @MockBean）    → DemoGreetingService 用 Mock
 *   ③ MockMvc → 无真实 HTTP 端口（直接调 DispatcherServlet）
 *   ④ 整个测试启动约 2~3 秒（vs @SpringBootTest 全量 20+ 秒）
 * </pre>
 */
@WebMvcTest(controllers = DemoGreetingController.class)
class DemoGreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoGreetingService demoGreetingService;

    @Test
    void greetingShouldReturnServiceResult() throws Exception {
        when(demoGreetingService.greet("Kafka")).thenReturn("Hello, Kafka!");

        mockMvc.perform(get("/api/demo/greeting").param("name", "Kafka"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Kafka!"));
    }

    @Test
    void greetingShouldUseDefaultParameter() throws Exception {
        when(demoGreetingService.greet("world")).thenReturn("Hello, world!");

        mockMvc.perform(get("/api/demo/greeting"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, world!"));
    }
}