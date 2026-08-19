package com.aiexplorer.researchagent; // 包声明：这个类属于 com.aiexplorer.researchagent 包，类似前端的文件路径

import org.springframework.boot.SpringApplication; // 导入 Spring Boot 启动器，提供 run() 方法
import org.springframework.boot.autoconfigure.SpringBootApplication; // 导入核心注解

/**
 * 研究智能体后端服务启动入口。
 *
 * 该类是整个 Spring Boot 应用唯一的"点火钥匙"：
 * - @SpringBootApplication 让框架自动扫描同包及子包下的所有组件（Controller、Service、Repository、Config）
 * - main 方法调用 SpringApplication.run()，启动内嵌 Tomcat，读取 application.yml，初始化所有 Bean
 */
@SpringBootApplication // 组合注解 = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class ResearchAgentApplication {

    /**
     * Java 程序的标准入口 main 方法。
     * SpringApplication.run() 做的事情：
     *   1. 创建 Spring IoC 容器（依赖注入容器）
     *   2. 扫描并实例化所有 @RestController、@Service、@Repository、@Configuration 类
     *   3. 读取 application.yml / application-dev.yml
     *   4. 启动内嵌 Tomcat，监听 server.port 端口（默认 8080）
     *   5. 初始化数据源连接池
     *
     * @param args 命令行参数（本项目未使用）
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用，传入当前类作为配置源
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}
