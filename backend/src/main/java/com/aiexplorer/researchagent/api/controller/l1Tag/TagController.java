package com.aiexplorer.researchagent.api.controller.l1Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiexplorer.researchagent.api.request.l1Tag.CreateTagRequestDto;
import com.aiexplorer.researchagent.api.request.l1Tag.UpdateTagRequestDto;
import com.aiexplorer.researchagent.api.vo.l1Tag.TagVo;
import com.aiexplorer.researchagent.application.service.l1Tag.TagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 标签 RESTful 接口（L1）。
 *
 * <p><b>五个接口</b>：创建(201) / 按 id 查(200) / 列表(200) / 更新(200) / 删除(204)。</p>
 *
 * <p><b>关键约定</b>：</p>
 * <ul>
 *   <li>对外只返回 {@link TagVo}（VO），不暴露 {@code TagEntity}（PO）——
 *       避免把数据库结构泄漏成 API 契约；</li>
 *   <li>路径参数用 {@code @PathVariable}，请求体用 {@code @RequestBody}
 *       （★ 必须来自 {@code org.springframework.web.bind.annotation}）；</li>
 *   <li>404/409 由 {@link TagExceptionHandler}（Tag 专用 Advice）统一处理，
 *       Controller 里不写 try-catch。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tags")
@Tag(name = "标签管理", description = "L1 标签域：创建 / 查询 / 更新 / 删除（含参数校验与统一错误码）")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** 创建标签：成功 201；重名 409（TAG_NAME_DUPLICATE）；参数非法 400（VALIDATION_ERROR）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建标签",
            description = "成功 201；重名 409（TAG_NAME_DUPLICATE）；参数非法 400（VALIDATION_ERROR + 字段明细）")
    public TagVo create(@RequestBody @Valid CreateTagRequestDto dto) {
        return tagService.createTag(dto);
    }

    /** 按 id 查询标签：成功 200；不存在 404（TAG_NOT_FOUND）。 */
    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询标签", description = "成功 200；不存在 404（TAG_NOT_FOUND）")
    public TagVo getById(
            @Parameter(description = "标签 id（UUID）", required = true) @PathVariable UUID id) {
        return tagService.getTagById(id);
    }

    /** 查询标签列表：全量返回（L3 加分页），按创建时间倒序。 */
    @GetMapping
    @Operation(summary = "查询标签列表", description = "全量返回（分页在 L3 加），按创建时间倒序")
    public List<TagVo> list() {
        return tagService.listTags();
    }

    /** 更新标签（部分更新，字段可空）：成功 200；不存在 404；改名冲突 409。 */
    @PutMapping("/{id}")
    @Operation(summary = "更新标签",
            description = "部分更新（字段为 null 表示不修改）；存在性 404；改名冲突 409")
    public TagVo update(
            @Parameter(description = "标签 id（UUID）", required = true) @PathVariable UUID id,
            @RequestBody @Valid UpdateTagRequestDto dto) {
        return tagService.updateTag(id, dto);
    }

    /** 删除标签：成功 204；不存在 404。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除标签", description = "成功 204；不存在 404（TAG_NOT_FOUND）")
    public void delete(
            @Parameter(description = "标签 id（UUID）", required = true) @PathVariable UUID id) {
        tagService.deleteTag(id);
    }
}
