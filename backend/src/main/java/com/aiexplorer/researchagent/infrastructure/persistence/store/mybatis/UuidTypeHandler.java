package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

/**
 * UUID 类型处理器。
 *
 * <p>MyBatis 3.5.19 核心包中未内置 UUID 的 TypeHandler，直接使用 #{id} 会报
 * "Type handler was null on parameter mapping for property 'id'"。
 * 与 JPA 对 UUID 开箱即用形成对照，此场景需要自行注册 TypeHandler。</p>
 *
 * <p>通过 @Component + @MappedTypes(UUID.class) 注册为 Spring Bean，
 * MyBatis Starter 会自动采集并注册到 SqlSessionFactory。</p>
 */
@Component
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement, int index, UUID parameter, JdbcType jdbcType) throws SQLException {
        preparedStatement.setObject(index, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, UUID.class);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, UUID.class);
    }

    @Override
    public UUID getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return callableStatement.getObject(columnIndex, UUID.class);
    }
}
