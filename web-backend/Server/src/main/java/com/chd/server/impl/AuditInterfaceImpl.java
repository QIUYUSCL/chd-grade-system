// 在远程服务端impl包中新建
package com.chd.server.impl;

import com.chd.server.dto.AuditLogDTO;
import com.chd.server.Interface.AuditInterface;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuditInterfaceImpl implements AuditInterface {
    private static final Logger log = LoggerFactory.getLogger(AuditInterfaceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    public AuditInterfaceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean logOperation(AuditLogDTO dto) {
        String sql = """
            INSERT INTO security_log 
                (operation_type, table_name, record_id, operator_id, operator_type, client_ip, operation_time) 
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """;
        try {
            jdbcTemplate.update(sql,
                    dto.getOperationType(),
                    dto.getTableName(),
                    dto.getRecordId(),
                    dto.getOperatorId(),
                    dto.getOperatorType(),
                    dto.getClientIp()
            );
            log.debug("审计日志记录成功: {} 操作 {} 表 {}", dto.getOperatorId(), dto.getOperationType(), dto.getTableName());
            return true;
        } catch (Exception e) {
            log.error("审计日志记录失败: {}", e.getMessage(), e);
            // 审计失败不影响主业务，但不返回false，避免客户端误判
            return true;
        }
    }
}