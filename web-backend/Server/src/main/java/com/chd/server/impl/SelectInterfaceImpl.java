package com.chd.server.impl;

import com.chd.server.dto.OperationDTO;
import com.chd.server.Interface.SelectInterface;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class SelectInterfaceImpl implements SelectInterface {

    private final JdbcTemplate jdbcTemplate;

    public SelectInterfaceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> select(OperationDTO dto) {
        validateTableName(dto.getTable());
        String sql = buildSelectSQL(dto);
        return jdbcTemplate.queryForMap(sql);
    }

    @Override
    public List<Map<String, Object>> selectList(OperationDTO dto) {
        validateTableName(dto.getTable());
        String sql = buildSelectSQL(dto);
        return jdbcTemplate.queryForList(sql);
    }

    private String buildSelectSQL(OperationDTO dto) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + dto.getTable());

        if (dto.getConditions() != null && !dto.getConditions().isEmpty()) {
            sql.append(" WHERE ");
            dto.getConditions().forEach((key, value) -> {
                if (value instanceof String) {
                    sql.append(key).append(" = '").append(value).append("' AND ");
                } else {
                    sql.append(key).append(" = ").append(value).append(" AND ");
                }
            });
            sql.setLength(sql.length() - 5); // 移除最后的AND
        }
        return sql.toString();
    }

    private void validateTableName(String table) {

        if (!table.matches("^(students|teachers|admins|grade_records|courses|student_courses|grade_analysis)$")) {
            throw new RuntimeException("非法表名: " + table);
        }
    }
}