package com.microlab.user.controller;

import com.microlab.user.model.UserProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户信息查询接口（教学用内存数据，不接数据库）。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    /** 内存中的用户数据：key = 用户 ID，value = 用户信息。 */
    private static final Map<Long, UserProfile> USER_PROFILE_STORE = new ConcurrentHashMap<>();

    static {
        USER_PROFILE_STORE.put(1L, new UserProfile(1L, "张三", "13800000001", "黄金会员"));
        USER_PROFILE_STORE.put(2L, new UserProfile(2L, "李四", "13800000002", "普通会员"));
    }

    /**
     * 按用户 ID 查询用户信息。
     *
     * @param userId 用户 ID
     * @return 用户信息；查不到时返回"游客"兜底对象
     */
    @GetMapping("/{userId}")
    public UserProfile queryUserProfile(@PathVariable("userId") Long userId) {
        return USER_PROFILE_STORE.getOrDefault(userId,
                new UserProfile(userId, "未知用户", "00000000000", "游客"));
    }
}
