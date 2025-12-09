package com.chd.server.impl;

import com.chd.server.dto.OperationDTO;
import com.chd.server.Interface.ManipulationInterface;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

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

        // 构建 UPDATE SQL
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");

        // SET 子句
        data.forEach((key, value) -> {
            sql.append(key).append(" = ");
            // 检查是否是SQL函数
            if (value instanceof String && "NOW()".equals(value)) {
                sql.append("NOW(), ");
            } else if (value instanceof String) {
                sql.append("'").append(value).append("', ");
            } else {
                sql.append(value).append(", ");
            }
        });
        sql.setLength(sql.length() - 2);

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
        } catch (DuplicateKeyException e) {
            // [新增] 修改时也捕获唯一性冲突
            throw new RuntimeException("已有成绩记录");
        } catch (Exception e) {
            // [修改] 抛出具体错误信息而不是简单的返回 false
            throw new RuntimeException("更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean insert(OperationDTO dto) {
        String table = dto.getTable();
        Map<String, Object> data = dto.getData();

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");

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
            return rows > 0;
        } catch (DuplicateKeyException e) {
            // [修改] 捕获唯一索引/主键冲突，返回更直观的提示
            throw new RuntimeException("已有成绩记录");
        } catch (Exception e) {
            // 其他数据库异常
            throw new RuntimeException("数据库操作失败: " + e.getMessage());
        }
    }


    // 实现删除逻辑
    @Override
    @Transactional
    public boolean delete(OperationDTO dto) {
        String table = dto.getTable();
        Map<String, Object> conditions = dto.getConditions();

        if (conditions == null || conditions.isEmpty()) {
            throw new RuntimeException("删除操作必须指定条件");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table).append(" WHERE ");

        conditions.forEach((key, value) -> {
            sql.append(key).append(" = ");
            if (value instanceof String) {
                sql.append("'").append(value).append("' AND ");
            } else {
                sql.append(value).append(" AND ");
            }
        });

        sql.setLength(sql.length() - 5); // 移除最后的 " AND "

        try {
            int rows = jdbcTemplate.update(sql.toString());
            return rows > 0;
        } catch (Exception e) {
            throw new RuntimeException("删除失败: " + e.getMessage());
        }
    }
}