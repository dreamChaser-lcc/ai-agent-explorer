package com.aiexplorer.researchagent.shared.exception;

import java.util.UUID;

/**
 * 标签不存在异常（L1）。
 *
 * <p>由 {@code TagExceptionHandler} 捕获 → 返回 404 + ErrorCode.TAG_NOT_FOUND。</p>
 */
public class TagNotFoundException extends RuntimeException {

    private final UUID id;

    public TagNotFoundException(UUID id) {
        super("标签不存在: " + id);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
