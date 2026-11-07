package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.requestPipline;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.requestPipline.RequestPiplineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 请求管线练习 · DAO 层：MyBatis Mapper（入口是 PO，不是 DTO——PO 才对应表）。
 */
@Mapper
public interface CreateOrUpdateMapper {

    int insert(@Param("po") RequestPiplineEntity po);

    int update(@Param("po") RequestPiplineEntity po);
}