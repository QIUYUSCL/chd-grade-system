package com.chd.score.remote.client.service;

import com.chd.score.remote.client.dto.OperationDTO;
import com.chd.score.remote.server.Interface.SelectInterface;
import com.chd.score.remote.server.Interface.ManipulationInterface;
import com.chd.score.security.encryption.BCryptUtil;
import com.chd.score.security.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;


@Service
public class RemoteClientService {
    private static final Logger log = LoggerFactory.getLogger(RemoteClientService.class);

    private final SelectInterface selectInterface;
    private final ManipulationInterface manipulationInterface;
    private final JwtConfig jwtConfig;
    private final JdbcTemplate jdbcTemplate;

    public RemoteClientService(SelectInterface selectInterface,
                               ManipulationInterface manipulationInterface,
                               JwtConfig jwtConfig,
                               JdbcTemplate jdbcTemplate) {
        this.selectInterface = selectInterface;
        this.manipulationInterface = manipulationInterface;
        this.jwtConfig = jwtConfig;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String executeLogin(OperationDTO dto, String clientIp) {

        log.debug("开始登录验证，用户ID: {}, 角色: {}",
                dto.getConditions() != null ? dto.getConditions().get("student_id") : null,
                dto.getRole());

        // 1. 调用远程服务查询用户
        Map<String, Object> userData = selectInterface.select(dto);

        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }


        // 2. 验证密码
        String storedHash = (String) userData.get("password_hash");
        String inputPassword = (String) dto.getData().get("password");

        log.debug("数据库密码哈希: {}, 输入密码存在: {}", storedHash, inputPassword != null);

        if (!BCryptUtil.verify(inputPassword, storedHash)) {
            throw new RuntimeException("密码错误");
        }

        log.debug("密码验证通过，用户ID: {}", userData.get("student_id") != null ?
                userData.get("student_id") : userData.get("teacher_id"));

        // 3. 生成JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userData.get("student_id") != null ?
                userData.get("student_id") : userData.get("teacher_id"));
        claims.put("role", dto.getRole());
        claims.put("name", userData.get("name"));
        claims.put("ip", clientIp);

        return jwtConfig.generateToken(claims);
    }

    /**
     * 修改密码
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @param userId 当前用户ID（从Token中获取）
     * @param role 用户角色（从Token中获取）
     * @param clientIp 客户端IP
     * @return 是否成功
     */
    public boolean changePassword(String oldPassword, String newPassword, String userId, String role, String clientIp) {
        log.info("用户 {} 正在修改密码", userId);

        // 1. 确定表名和主键字段
        String tableName;
        String primaryKeyField;

        if ("STUDENT".equals(role)) {
            tableName = "students";
            primaryKeyField = "student_id";
        } else if ("TEACHER".equals(role)) {
            tableName = "teachers";
            primaryKeyField = "teacher_id";
        } else if ("ADMIN".equals(role)) {
            tableName = "admins";
            primaryKeyField = "admin_id";
        } else {
            throw new RuntimeException("无效的用户角色: " + role);
        }

        // 2. 查询当前用户的密码哈希
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable(tableName);
        queryDTO.setConditions(Map.of(primaryKeyField, userId));

        Map<String, Object> userData = selectInterface.select(queryDTO);

        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        // 3. 验证旧密码
        String storedHash = (String) userData.get("password_hash");
        if (!BCryptUtil.verify(oldPassword, storedHash)) {
            throw new RuntimeException("旧密码错误");
        }

        // 4. 加密新密码
        String newHash = BCryptUtil.hash(newPassword);

        // 5. 更新数据库
        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable(tableName);
        updateDTO.setData(Map.of("password_hash", newHash));
        updateDTO.setConditions(Map.of(primaryKeyField, userId));

        boolean success = manipulationInterface.update(updateDTO);

        if (success) {
            log.info("用户 {} 密码修改成功", userId);

            // 6. 记录安全审计日志（核心补充）
            insertSecurityLog(
                    "UPDATE",           // operation_type
                    tableName,          // table_name
                    userId,             // record_id
                    userId,             // operator_id（自己修改自己）
                    role,               // operator_type
                    clientIp,           // client_ip
                    null                // data_hash（密码操作不记录敏感数据哈希）
            );
        } else {
            log.error("用户 {} 密码修改失败", userId);
        }

        return success;
    }

    /**
     * 插入安全审计日志（私有辅助方法）
     */
    private void insertSecurityLog(String operationType, String tableName, String recordId,
                                   String operatorId, String operatorType, String clientIp, String dataHash) {
        String sql = """
            INSERT INTO security_log 
                (operation_type, table_name, record_id, operator_id, operator_type, client_ip, operation_time, data_hash) 
            VALUES 
                (?, ?, ?, ?, ?, ?, NOW(), ?)
            """;

        try {
            jdbcTemplate.update(sql, operationType, tableName, recordId, operatorId, operatorType, clientIp, dataHash);
            log.debug("安全审计日志记录成功: 用户 {} {} 表 {}", operatorId, operationType, tableName);
        } catch (Exception e) {
            log.error("安全审计日志记录失败: {}", e.getMessage(), e);
            // 审计日志失败不影响主业务，不抛出异常
        }
    }


}