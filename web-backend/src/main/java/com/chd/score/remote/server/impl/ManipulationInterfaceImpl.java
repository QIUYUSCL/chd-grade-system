package com.chd.score.remote.server.impl;

import com.chd.score.remote.client.dto.OperationDTO;
import com.chd.score.remote.server.Interface.ManipulationInterface;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ManipulationInterfaceImpl implements ManipulationInterface {

    private final JdbcTemplate jdbcTemplate;

    private static final Logger log = LoggerFactory.getLogger(ManipulationInterfaceImpl.class);

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

    @Override
    @Transactional
    public boolean insert(OperationDTO dto) {
        String table = dto.getTable();
        Map<String, Object> data = dto.getData();

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");
        log.info("执行INSERT SQL: {}", sql);

        data.forEach((key, value) -> {
            sql.append(key).append(", ");
            if (value instanceof String) {
                values.append("'").append(value).append("', ");
            } else {
                values.append(value).append(", ");
            }
        });

        sql.setLength(sql.length() - 2); // 移除逗号
        values.setLength(values.length() - 2);

        sql.append(")").append(values).append(")");

        try {
            int rows = jdbcTemplate.update(sql.toString());
            log.info("INSERT影响行数: {}", rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("INSERT执行失败 - SQL: {}, 异常: {}", sql, e.getMessage(), e);
            throw new RuntimeException("数据库操作失败: " + e.getMessage());
        }
    }

}