package com.microlab.user.model;

/**
 * 用户信息（教学用简单模型，不接数据库）。
 *
 * @param userId    用户 ID
 * @param userName  用户名
 * @param userPhone 手机号
 * @param userLevel 用户等级
 */
public record UserProfile(Long userId, String userName, String userPhone, String userLevel) {
}
