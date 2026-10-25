package com.aiexplorer.researchagent.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 通用分页响应壳（场景③ 引入）。
 *
 * <p>列表接口从"裸数组"升级为标准分页结构——前端"第 x 页 / 共 y 页 / 共 z 条"就靠它。
 * 采用行业标准形状（与 Spring Data 的 Page、各类前端表格组件的约定一致）。</p>
 *
 * @param content       当前页数据
 * @param page          当前页码（从 0 开始——与大多数分页 API 约定一致）
 * @param size          每页条数
 * @param totalElements 满足筛选条件的总条数
 * @param totalPages    总页数（由服务端计算，前端不必自己算）
 * @param <T>           元素类型（泛型——J3 知识）
 */
@Schema(name = "PageResponse", description = "通用分页响应壳（content + 分页元信息）")
public record PageResponse<T>(
        @Schema(description = "当前页数据列表")
        List<T> content,

        @Schema(description = "当前页码（从 0 开始）", example = "0")
        int page,

        @Schema(description = "每页条数（服务端上限 100）", example = "20")
        int size,

        @Schema(description = "满足筛选条件的总条数", example = "36")
        long totalElements,

        @Schema(description = "总页数（服务端计算，向上取整）", example = "2")
        int totalPages) {
}
