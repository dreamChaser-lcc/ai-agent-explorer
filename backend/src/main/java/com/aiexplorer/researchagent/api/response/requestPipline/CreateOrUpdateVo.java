package com.aiexplorer.researchagent.api.response.requestPipline;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建或更新请求VO
 */
@Schema(name = "CreateOrUpdateVo", description = "创建或更新请求VO")
public record CreateOrUpdateVo(
    @Schema(description = "请求id", example = "1234567890") String requestId,
    @Schema(description = "请求描述", example = "创建请求") String desc,
    @Schema(description = "创建时间", example = "2025-12-12 12:12:12") String createTime) {
}
