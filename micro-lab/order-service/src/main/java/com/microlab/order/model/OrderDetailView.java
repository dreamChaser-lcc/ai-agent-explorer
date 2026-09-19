package com.microlab.order.model;

/**
 * 订单详情视图：订单信息 + 所属用户信息（跨服务聚合的结果）。
 *
 * @param orderId     订单 ID
 * @param orderTitle  订单标题
 * @param orderAmount 订单金额（分）
 * @param userProfile 下单用户信息（来自用户服务的远程调用结果）
 */
public record OrderDetailView(Long orderId, String orderTitle, Long orderAmount,
                              UserProfileView userProfile) {
}
