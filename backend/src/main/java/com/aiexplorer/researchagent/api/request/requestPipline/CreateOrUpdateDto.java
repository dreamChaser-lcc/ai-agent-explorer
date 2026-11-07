package com.aiexplorer.researchagent.api.request.requestPipline;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建或更新请求 DTO（请求体）。
 *
 * <p><b>为什么是普通 class + 手写 getter/setter</b>：功能上 record 最简洁，
 * 但本机 IDE 的索引对 record 隐式访问器、以及 Lombok 生成的 getter 识别不稳定，
 * 反复误报 "method undefined"。手写后 IDE 与编译器的认知完全一致，不再误报。</p>
 *
 * <p>IDE 恢复正常后可换回（任选其一，功能等价）：
 * <ul>
 *   <li>record 版：{@code public record CreateOrUpdateDto(String requestId, RequestType requestType, String desc) {}}</li>
 *   <li>Lombok 版：class 上加 {@code @Getter @Setter @NoArgsConstructor}</li>
 * </ul></p>
 */
@Schema(name = "CreateOrUpdateDto", description = "创建或更新请求")
public class CreateOrUpdateDto {

    @Schema(description = "请求id", example = "1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @Schema(description = "请求类型", example = "CREATE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "requestType 不能为空")
    private RequestType requestType;

    @Schema(description = "请求描述", example = "创建请求", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "desc 不能为空")
    private String desc;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}