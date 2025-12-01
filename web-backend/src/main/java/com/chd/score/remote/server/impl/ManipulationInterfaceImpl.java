package com.chd.score.remote.server.impl;

import com.chd.score.remote.client.dto.OperationDTO;
import com.chd.score.remote.server.Interface.ManipulationInterface;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class ManipulationInterfaceImpl implements ManipulationInterface {

    private final JdbcTemplate jdbcTemplate;

    public ManipulationInterfaceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean update(OperationDTO dto) {
        String table = dto.getTable();
        Map<String, Object> data = dto.getData();
        Map<String, Object> conditions = dto.getConditions();

        // 构建 UPDATE SQL（注意：生产环境应使用参数化查询防止SQL注入）
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");

        // SET 子句
        data.forEach((key, value) -> {
            sql.append(key).append(" = ");
            if (value instanceof String) {
                sql.append("'").append(value).append("', ");
            } else {
                sql.append(value).append(", ");
            }
        });
        sql.setLength(sql.length() - 2); // 移除最后一个逗号

        // WHERE 子句
        sql.append(" WHERE ");
        conditions.forEach((key, value) -> {
            sql.append(key).append(" = ");
            if (value instanceof String) {
                sql.append("'").append(value).append("' AND ");
            } else {
                sql.append(value).append(" AND ");
            }
        });
        sql.setLength(sql.length() - 5); // 移除最后一个 AND

        try {
            int rows = jdbcTemplate.update(sql.toString());
            return rows > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}