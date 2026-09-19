package com.microlab.order.model;

/**
 * 订单信息（教学用简单模型，不接数据库）。
 *
 * @param orderId     订单 ID
 * @param orderTitle  订单标题
 * @param orderAmount 订单金额（单位：分，避免浮点误差）
 * @param userId      下单用户 ID（后续站点用它调用用户服务）
 */
public record OrderInfo(Long orderId, String orderTitle, Long orderAmount, Long userId) {
}
