package com.microlab.order.client;

import com.microlab.order.model.UserProfileView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务的 Feign 客户端：声明式远程调用。
 *
 * <p>关键点：{@code name} 写的是<b>服务名</b>（Nacos 里的注册名），而不是 IP——
 * 调用发生时，Feign 会向 Nacos 查询 {@code user-service} 的实例列表，
 * 经负载均衡挑选一台实例，再拼接 URL 发起 HTTP 请求。</p>
 *
 * <p>这个接口不需要自己写实现类：加了 {@code @EnableFeignClients} 后，
 * Spring 启动时会为它生成动态代理实现。</p>
 *
 * <p>第 7 站新增：{@code fallback} 指定"降级兜底"实现——当 user-service 不可用
 * （连接失败 / 超时 / 被熔断拦截）时，自动走兜底逻辑返回降级数据，而不是抛异常。</p>
 */
@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * 远程调用用户服务的查询接口：GET /users/{userId}。
     *
     * @param userId 用户 ID
     * @return 用户信息视图（响应 JSON 自动反序列化为对象）
     */
    @GetMapping("/users/{userId}")
    UserProfileView queryUserProfile(@PathVariable("userId") Long userId);
}
