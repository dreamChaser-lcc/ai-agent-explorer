package com.aiexplorer.researchagent.api.request.l1Tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新标签请求参数（L1）。
 *
 * <p>三个字段都可选（null = 不修改）：
 * <ul>
 *   <li>name：改名（服务端会校验重名）</li>
 *   <li>description：改描述</li>
 *   <li>status：ACTIVE / DISABLED</li>
 * </ul>
 * 更新时间为服务端维护，客户端不传。</p>
 */
@Data
@Schema(description = "更新标签请求参数")
public class UpdateTagRequestDto {

    /** 新名称（可选）：传了才改，且会校验重名。 */
    @Size(min = 2, max = 32, message = "标签名称长度需在 2~32 个字符之间")
    @Pattern(regexp = "^[\\w\\u4e00-\\u9fa5-]+$", message = "标签名称只能包含中文、字母、数字、连字符或下划线")
    @Schema(description = "标签名称（不传表示不修改）", example = "数据库")
    private String name;

    /** 新描述（可选）。 */
    @Size(max = 200, message = "标签描述长度不能超过 200 个字符")
    @Schema(description = "标签描述（不传表示不修改）")
    private String description;

    /** 新状态（可选）：ACTIVE / DISABLED。 */
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "标签状态只能是 ACTIVE 或 DISABLED")
    @Schema(description = "标签状态（不传表示不修改）", example = "ACTIVE")
    private String status;
}
