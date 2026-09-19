package com.microlab.order.controller;

import com.microlab.order.client.UserServiceClient;
import com.microlab.order.model.OrderDetailView;
import com.microlab.order.model.OrderInfo;
import com.microlab.order.model.UserProfileView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单查询接口（教学用内存数据，不接数据库）。
 *
 * <p>查询订单时通过 OpenFeign 远程调用用户服务，把订单与用户信息聚合后返回。</p>
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    /** 内存中的订单数据：key = 订单 ID，value = 订单信息。 */
    private static final Map<Long, OrderInfo> ORDER_INFO_STORE = new ConcurrentHashMap<>();

    static {
        ORDER_INFO_STORE.put(1001L, new OrderInfo(1001L, "AI 研究服务订阅（月付）", 9900L, 1L));
        ORDER_INFO_STORE.put(1002L, new OrderInfo(1002L, "数据存储扩容包", 1990L, 2L));
    }

    /** 用户服务的 Feign 客户端（由 Spring 注入动态代理实现）。 */
    private final UserServiceClient userServiceClient;

    public OrderController(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /**
     * 按订单 ID 查询订单详情（订单信息 + 所属用户信息）。
     *
     * @param orderId 订单 ID
     * @return 订单详情；订单不存在时返回兜底对象
     */
    @GetMapping("/{orderId}")
    public OrderDetailView queryOrderInfo(@PathVariable("orderId") Long orderId) {
        OrderInfo orderInfo = ORDER_INFO_STORE.getOrDefault(orderId,
                new OrderInfo(orderId, "未知订单", 0L, 0L));

        // 跨服务调用：以服务名 user-service 请求用户信息
        // （地址解析 + 负载均衡由 Feign + LoadBalancer 自动完成，全程无需写 IP）
        UserProfileView userProfile = userServiceClient.queryUserProfile(orderInfo.userId());

        return new OrderDetailView(orderInfo.orderId(), orderInfo.orderTitle(),
                orderInfo.orderAmount(), userProfile);
    }
}
