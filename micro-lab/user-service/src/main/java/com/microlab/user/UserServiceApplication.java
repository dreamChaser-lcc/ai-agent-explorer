package com.microlab.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 *
 * <p>微服务教学项目 micro-lab 的第一个服务：对外提供用户信息查询接口。</p>
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
