package com.aiexplorer.researchagent.api.controller.requestPipline;

import com.aiexplorer.researchagent.api.request.requestPipline.CreateOrUpdateDto;
import com.aiexplorer.researchagent.api.request.requestPipline.RequestType;
import com.aiexplorer.researchagent.api.response.requestPipline.CreateOrUpdateVo;
import com.aiexplorer.researchagent.application.service.requestPipline.CreateOrUpdateService;
import com.aiexplorer.researchagent.infrastructure.aop.requestPipline.RequestPiplineLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请求管线练习 · 第 1 环：Controller。
 *
 * <p>这一环只做三件事：接参数（DTO）→ 调 Service → 返回 VO。
 * 后续环（Filter/Interceptor/AOP/异常）都围绕它"加包装"。</p>
 */
@RestController
@RequestMapping("/api/request-pipline")
@Tag(name = "请求 pipeline（从 0 手写）", description = "Controller → Service → VO 的最小闭环")
public class RequestPiplineController {

    private final CreateOrUpdateService createOrUpdateService;

    public RequestPiplineController(CreateOrUpdateService createOrUpdateService) {
        this.createOrUpdateService = createOrUpdateService;
    }

    @Operation(summary = "创建或更新", description = "请求进入管线的第一环")
    @PostMapping("/create")
    @RequestPiplineLog("第 1 环：Controller")
    public CreateOrUpdateVo create(@Valid @RequestBody CreateOrUpdateDto dto) {
        System.out.println("[Controller] 收到请求 requestId=" + dto.getRequestId()
                + " type=" + dto.getRequestType());
        // 按请求类型分流（CREATE / UPDATE 走不同 Service 方法）
        if (dto.getRequestType() == RequestType.UPDATE) {
            return createOrUpdateService.update(dto);
        }
        return createOrUpdateService.create(dto);
    }
}