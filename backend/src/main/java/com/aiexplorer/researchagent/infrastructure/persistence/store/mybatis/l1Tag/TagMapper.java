package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.l1Tag;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.l1Tag.TagEntity;

/**
 * 标签表（{@code l1_tag}）的 MyBatis Mapper。
 *
 * <p><b>三个约定</b>（L1 标签域）：</p>
 * <ol>
 *   <li>与 {@code resources/mapper/l1Tag/TagMapper.xml} 一一对应，
 *       XML 的 {@code namespace} 必须等于本接口的全限定名（多一级或少一级都会报
 *       {@code Invalid bound statement (not found)}）；</li>
 *   <li>多参数方法用 {@link Param} 起名，XML 里用 {@code #{名字}} 引用；</li>
 *   <li>返回 null 表示"查不到"（是否抛 404 由 Service 层决定，Mapper 不抛业务异常）。</li>
 * </ol>
 */
@Mapper
public interface TagMapper {

    /** 新增标签；返回影响行数（1 = 成功）。主键由 Java 侧生成（UUID）。 */
    int insert(@Param("po") TagEntity po);

    /** 按主键查询；不存在返回 {@code null}。 */
    TagEntity selectById(@Param("id") UUID id);

    /** 按名称精确查询（重名校验用）；不存在返回 {@code null}。 */
    TagEntity selectByName(@Param("name") String name);

    /** 查询全部标签（按创建时间倒序，见 XML 的 ORDER BY）。 */
    List<TagEntity> selectAll();

    /** 更新标签（name / description / status / update_time 按主键更新）；返回影响行数。 */
    int update(@Param("po") TagEntity po);

    /** 按主键删除；返回影响行数。 */
    int deleteById(@Param("id") UUID id);
}
