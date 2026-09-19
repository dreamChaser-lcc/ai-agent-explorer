package com.microlab.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类（第 6 站）。
 *
 * <p>微服务教学项目 micro-lab 的统一入口：所有外部请求先到达这里，
 * 由网关按配置的路由规则（见 application.yml）转发给对应微服务。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
