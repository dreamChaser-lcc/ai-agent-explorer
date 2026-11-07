package com.aiexplorer.researchagent.api.request.requestPipline;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 请求类型（练习用枚举：CREATE 走新建、UPDATE 走更新）。
 */
@Schema(name = "RequestType", description = "请求类型")
public enum RequestType {
    CREATE,
    UPDATE
}