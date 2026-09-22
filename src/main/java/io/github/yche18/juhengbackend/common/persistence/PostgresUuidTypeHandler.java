package io.github.yche18.juhengbackend.common.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * 在 Java UUID 与 PostgreSQL 原生 UUID 列之间执行显式类型映射。
 *
 * <p>MyBatis 本身没有内置 UUID TypeHandler；显式处理可以保留数据库 UUID
 * 约束和索引语义，而不需要把领域标识降级为字符串。</p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID>
{

    /**
     * 以 PostgreSQL OTHER 类型绑定非空 UUID 参数。
     *
     * @param preparedStatement 当前 SQL 预编译语句
     * @param index 参数位置
     * @param parameter UUID 参数
     * @param jdbcType MyBatis 解析的 JDBC 类型
     * @throws SQLException JDBC 绑定失败时抛出
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            UUID parameter,
            JdbcType jdbcType) throws SQLException
    {
        preparedStatement.setObject(index, parameter, Types.OTHER);
    }

    /**
     * 按列名读取 PostgreSQL UUID。
     *
     * @param resultSet 查询结果集
     * @param columnName 列名
     * @return UUID 或 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return resultSet.getObject(columnName, UUID.class);
    }

    /**
     * 按列位置读取 PostgreSQL UUID。
     *
     * @param resultSet 查询结果集
     * @param columnIndex 列位置
     * @return UUID 或 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return resultSet.getObject(columnIndex, UUID.class);
    }

    /**
     * 从存储过程输出参数读取 PostgreSQL UUID。
     *
     * @param callableStatement 存储过程语句
     * @param columnIndex 列位置
     * @return UUID 或 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException
    {
        return callableStatement.getObject(columnIndex, UUID.class);
    }
}
