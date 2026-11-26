package com.aiexplorer.researchagent.api.request.l1Tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建标签请求参数（L1）。
 *
 * <p>只包含"客户端能决定"的字段：名称、描述。
 * 状态/时间由服务端生成（客户端传了也没用，干脆不接收——契约更清晰）。</p>
 */
@Data
@Schema(description = "创建标签请求参数")
public class CreateTagRequestDto {

    /** 标签名：2~32 字符，允许中英文/数字/连字符/下划线。 */
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 2, max = 32, message = "标签名称长度需在 2~32 个字符之间")
    @Pattern(regexp = "^[\\w\\u4e00-\\u9fa5-]+$", message = "标签名称只能包含中文、字母、数字、连字符或下划线")
    @Schema(description = "标签名称", example = "数据库")
    private String name;

    /** 描述：可为空，最长 200 字符。 */
    @Size(max = 200, message = "标签描述长度不能超过 200 个字符")
    @Schema(description = "标签描述", example = "与数据库相关的任务")
    private String description;
}
