package com.microlab.order.model;

/**
 * 用户信息视图对象（订单服务侧的 DTO）。
 *
 * <p>微服务实践说明：服务之间通过 HTTP + JSON 通信，调用方各自定义"本次调用所需"的
 * 视图结构（DTO），而不共享提供方的实体类——只要双方约定的 JSON 字段一致即可。
 * 这样两个服务的模型可以独立演进。</p>
 *
 * @param userId    用户 ID
 * @param userName  用户名
 * @param userPhone 手机号
 * @param userLevel 用户等级
 */
public record UserProfileView(Long userId, String userName, String userPhone, String userLevel) {
}
