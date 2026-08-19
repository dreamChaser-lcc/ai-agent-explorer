package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

/**
 * JsonNode 与数据库 JSON 列之间的类型转换处理器。
 *
 * <p>JPA 侧用 Hibernate 的 @JdbcTypeCode(SqlTypes.JSON) 处理 JSON 列；
 * MyBatis 没有内置 JsonNode 支持，需要自定义 TypeHandler：
 * 写入时序列化为字符串，读取时反序列化为 JsonNode。</p>
 *
 * <p>通过 @Component 注册为 Spring Bean，MyBatis Starter 会自动采集并注册到 SqlSessionFactory。</p>
 */
@Component
@MappedTypes(JsonNode.class)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    private final ObjectMapper objectMapper;

    public JsonNodeTypeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement, int index, JsonNode parameter, JdbcType jdbcType) throws SQLException {
        try {
            preparedStatement.setString(index, objectMapper.writeValueAsString(parameter));
        } catch (IOException exception) {
            throw new SQLException("序列化 JsonNode 到 JSON 列失败", exception);
        }
    }

    @Override
    public JsonNode getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parseToJsonNode(resultSet.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parseToJsonNode(resultSet.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return parseToJsonNode(callableStatement.getString(columnIndex));
    }

    private JsonNode parseToJsonNode(String columnValue) throws SQLException {
        if (columnValue == null) {
            return null;
        }
        try {
            JsonNode parsedNode = objectMapper.readTree(columnValue);
            // H2 把 JSON 列读回为带引号的字符串字面量（如 "{\"key\":\"value\"}"），
            // 首次解析会得到 TextNode 而非对象结构，需要二次解析还原真实 JSON。
            // PostgreSQL 原生返回 JSON 文本，首次解析即为对象，不进入该分支。
            if (parsedNode.isTextual()) {
                JsonNode unquotedNode = objectMapper.readTree(parsedNode.asText());
                if (unquotedNode != null && !unquotedNode.isNull()) {
                    return unquotedNode;
                }
            }
            return parsedNode;
        } catch (IOException exception) {
            throw new SQLException("解析 JSON 列为 JsonNode 失败", exception);
        }
    }
}
