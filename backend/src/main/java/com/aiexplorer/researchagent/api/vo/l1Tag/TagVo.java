package com.aiexplorer.researchagent.api.vo.l1Tag;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 标签视图对象（VO）——接口对外的稳定契约。
 *
 * <p><b>为什么单独一个 VO，而不是直接返回 TagEntity（PO）：</b></p>
 * <ul>
 *   <li>PO 是"数据库行的镜像"，字段随时可能因表结构变化而变；
 *       VO 是"对外承诺的契约"，字段增减要评估兼容性；</li>
 *   <li>两者解耦后，改表结构不会直接破坏 API（在 Service 层做转换）；</li>
 *   <li>时间字段转成字符串（ISO-8601），避免前端/其它语言处理时区对象麻烦。</li>
 * </ul>
 *
 * <p>使用 record：不可变 + 自动生成 equals/hashCode/toString（VO 正好需要）。</p>
 */
@Schema(name = "TagVo", description = "标签视图对象（对外契约）")
public record TagVo(

        @Schema(description = "标签 id（UUID 字符串）", example = "65d695b3-7536-46d3-9798-6706430fd6b0")
        String id,

        @Schema(description = "标签名称", example = "数据库")
        String name,

        @Schema(description = "标签描述", example = "与数据库相关的任务")
        String description,

        @Schema(description = "标签状态：ACTIVE / DISABLED", example = "ACTIVE")
        String status,

        @Schema(description = "创建时间（ISO-8601 字符串）", example = "2026-11-26T16:10:35.650955200+08:00")
        String createTime,

        @Schema(description = "更新时间（ISO-8601 字符串）", example = "2026-11-26T16:10:35.827637200+08:00")
        String updateTime) {
}
