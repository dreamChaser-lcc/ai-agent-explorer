package com.aiexplorer.researchagent.api.controller.l1Tag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aiexplorer.researchagent.api.request.l1Tag.CreateTagRequestDto;
import com.aiexplorer.researchagent.api.vo.l1Tag.TagVo;
import com.aiexplorer.researchagent.application.service.l1Tag.TagService;
import com.aiexplorer.researchagent.shared.exception.TagNameDuplicateException;

/**
 * L1 标签接口切片测试（@WebMvcTest：只装 Web 层 + Tag 专用 Advice）。
 *
 * <p>三个用例覆盖 L1 验收的关键路径：</p>
 * <ul>
 *   <li>创建成功 → 201 + 返回体；</li>
 *   <li>重名 → 409 + TAG_NAME_DUPLICATE（验证 TagExceptionHandler 生效）；</li>
 *   <li>参数非法 → 400 + 字段明细（验证 @Valid + 全局校验处理）。</li>
 * </ul>
 */
@WebMvcTest(controllers = TagController.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @Test
    void createShouldReturn201WithBody() throws Exception {
        TagVo response = new TagVo("id-1", "数据库", "与数据库相关", "ACTIVE", "2026-11-26T10:00:00", "2026-11-26T10:00:00");
        when(tagService.createTag(any(CreateTagRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据库\",\"description\":\"与数据库相关\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("数据库"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createShouldReturn409WhenNameDuplicate() throws Exception {
        when(tagService.createTag(any(CreateTagRequestDto.class)))
                .thenThrow(new TagNameDuplicateException("数据库"));

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数据库\",\"description\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_NAME_DUPLICATE"));
    }

    @Test
    void createShouldReturn400WhenNameInvalid() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("name"));
    }
}
