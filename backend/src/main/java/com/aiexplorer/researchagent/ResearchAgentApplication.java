package com.aiexplorer.researchagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 研究智能体后端服务启动入口。
 */
@SpringBootApplication
public class ResearchAgentApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}
