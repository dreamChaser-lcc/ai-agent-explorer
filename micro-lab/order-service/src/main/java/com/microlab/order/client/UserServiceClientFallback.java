package com.microlab.order.client;

import com.microlab.order.model.UserProfileView;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Feign 客户端的"降级兜底"实现（第 7 站）。
 *
 * <p>当对 user-service 的调用失败（服务不可用、超时、被熔断拦截）时，
 * Feign 不会把异常抛给业务代码，而是转而调用这里的同名方法返回"兜底值"，
 * 保证订单服务自身依然可用——即"局部失败不扩散"。</p>
 *
 * <p>必须注册为 Spring Bean（{@code @Component}），Feign 才能找到兜底实现。</p>
 */
@Component
public class UserServiceClientFallback implements UserServiceClient {

    /**
     * 降级兜底：返回占位用户信息。
     *
     * @param userId 用户 ID
     * @return 标记为"降级兜底"的占位信息（方便调用方识别这不是真实数据）
     */
    @Override
    public UserProfileView queryUserProfile(Long userId) {
        return new UserProfileView(userId, "用户服务暂不可用", "-", "降级兜底");
    }
}
