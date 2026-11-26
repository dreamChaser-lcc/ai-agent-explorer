package com.aiexplorer.researchagent.application.service.l1Tag;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aiexplorer.researchagent.api.request.l1Tag.CreateTagRequestDto;
import com.aiexplorer.researchagent.api.request.l1Tag.UpdateTagRequestDto;
import com.aiexplorer.researchagent.api.vo.l1Tag.TagVo;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.l1Tag.TagEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.l1Tag.TagMapper;
import com.aiexplorer.researchagent.shared.exception.TagNameDuplicateException;
import com.aiexplorer.researchagent.shared.exception.TagNotFoundException;

/**
 * 标签应用服务（L1）。
 *
 * <p><b>职责</b>：编排"业务规则 + 持久化 + 转换"，Controller 只负责参数绑定。</p>
 *
 * <p><b>L1 的业务规则（全部在这层）</b>：</p>
 * <ul>
 *   <li>创建：重名检测（先查后插，L4 会加数据库唯一约束兜底）；状态默认 ACTIVE；</li>
 *   <li>查询/更新/删除：不存在 → {@link TagNotFoundException}（→404）；</li>
 *   <li>更新：部分更新（null 字段不动），改名时校验重名（→409）；</li>
 *   <li>转换：PO（TagEntity）→ VO（{@link TagVo}），保证实体不越过 Controller 边界。</li>
 * </ul>
 *
 * <p><b>事务</b>：L1 均为单表操作，暂不加 {@code @Transactional}（L2 统一讲事务边界与传播）。</p>
 */
@Service
public class TagService {

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /** 创建标签：重名抛 409，状态默认 ACTIVE，返回创建后的 VO。 */
    public TagVo createTag(CreateTagRequestDto dto) {
        if (tagMapper.selectByName(dto.getName()) != null) {
            throw new TagNameDuplicateException(dto.getName());
        }
        OffsetDateTime now = OffsetDateTime.now();
        TagEntity entity = new TagEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(TagStatus.ACTIVE.name());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        tagMapper.insert(entity);
        return toVo(entity);
    }

    /** 按 id 查询：不存在抛 404。 */
    public TagVo getTagById(UUID id) {
        return toVo(requireExisting(id));
    }

    /** 按名称查询（内部使用；不存在返回 null，不抛异常）。 */
    public TagVo getTagByName(String name) {
        TagEntity entity = tagMapper.selectByName(name);
        return entity == null ? null : toVo(entity);
    }

    /** 全量列表（排序由 XML 决定：创建时间倒序）。 */
    public List<TagVo> listTags() {
        return tagMapper.selectAll().stream().map(this::toVo).toList();
    }

    /** 更新（部分更新）：不存在 404；改名冲突 409。 */
    public TagVo updateTag(UUID id, UpdateTagRequestDto dto) {
        TagEntity entity = requireExisting(id);

        if (dto.getName() != null && !dto.getName().equals(entity.getName())) {
            if (tagMapper.selectByName(dto.getName()) != null) {
                throw new TagNameDuplicateException(dto.getName());
            }
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        entity.setUpdateTime(OffsetDateTime.now());
        tagMapper.update(entity);
        return toVo(entity);
    }

    /** 删除：不存在抛 404。 */
    public void deleteTag(UUID id) {
        requireExisting(id);
        tagMapper.deleteById(id);
    }

    /** 统一"取不到就 404"，避免每个方法重复 if。 */
    private TagEntity requireExisting(UUID id) {
        TagEntity entity = tagMapper.selectById(id);
        if (entity == null) {
            throw new TagNotFoundException(id);
        }
        return entity;
    }

    /** PO → VO（实体不越过 Controller 边界）。 */
    private TagVo toVo(TagEntity entity) {
        return new TagVo(
                entity.getId() == null ? null : entity.getId().toString(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreateTime() == null ? null : entity.getCreateTime().toString(),
                entity.getUpdateTime() == null ? null : entity.getUpdateTime().toString());
    }
}
