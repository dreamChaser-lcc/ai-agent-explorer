package com.aiexplorer.researchagent.shared.exception;

/**
 * 标签名重复异常（L1）。
 *
 * <p>由 {@code TagExceptionHandler} 捕获 → 返回 409 + ErrorCode.TAG_NAME_DUPLICATE。</p>
 *
 * <p>说明：L1 用"先查后插"检测（有并发窗口）；L4 会补数据库唯一约束兜底
 * （唯一索引 + 捕获 DuplicateKeyException），届时本异常也会从冲突处抛出。</p>
 */
public class TagNameDuplicateException extends RuntimeException {

    private final String name;

    public TagNameDuplicateException(String name) {
        super("标签名已存在: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
