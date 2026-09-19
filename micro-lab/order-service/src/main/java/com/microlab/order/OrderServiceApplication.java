package com.microlab.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类。
 *
 * <p>微服务教学项目 micro-lab 的第二个服务：对外提供订单查询接口。
 * 通过 OpenFeign 调用用户服务，补齐订单所属用户的信息。</p>
 *
 * <p>{@code @EnableFeignClients}：开启 Feign 扫描，启动时为本工程下的
 * {@code @FeignClient} 接口生成动态代理实现类。</p>
 */
@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
