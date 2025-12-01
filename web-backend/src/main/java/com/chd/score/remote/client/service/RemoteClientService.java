package com.chd.score.remote.client.service;

import com.chd.score.remote.client.dto.OperationDTO;
import com.chd.score.remote.server.Interface.SelectInterface;
import com.chd.score.remote.server.Interface.ManipulationInterface;
import com.chd.score.security.encryption.AESUtil;
import com.chd.score.security.encryption.BCryptUtil;
import com.chd.score.security.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.chd.score.remote.client.dto.AuditLogDTO;
import com.chd.score.remote.server.Interface.AuditInterface;

@Service
public class RemoteClientService {
    private static final Logger log = LoggerFactory.getLogger(RemoteClientService.class);

    private final SelectInterface selectInterface;          // 远程调用：查询接口
    private final ManipulationInterface manipulationInterface;      // 远程调用：操作接口
    private final AuditInterface auditInterface;  // 远程审计接口
    private final JwtConfig jwtConfig;

    public RemoteClientService(SelectInterface selectInterface,
                               ManipulationInterface manipulationInterface,
                               AuditInterface auditInterface,
                               JwtConfig jwtConfig) {
        this.selectInterface = selectInterface;
        this.manipulationInterface = manipulationInterface;
        this.auditInterface = auditInterface;
        this.jwtConfig = jwtConfig;
    }

    /**
     * 执行登录验证
     */
    public String executeLogin(OperationDTO dto, String clientIp) {
        log.debug("开始登录验证，用户ID: {}, 角色: {}",
                dto.getConditions() != null ? dto.getConditions().get("student_id") : null,
                dto.getRole());

        Map<String, Object> userData = selectInterface.select(dto);

        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码
        String storedHash = (String) userData.get("password_hash");
        String inputPassword = (String) dto.getData().get("password");

        log.debug("数据库密码哈希: {}, 输入密码存在: {}", storedHash, inputPassword != null);

        if (!BCryptUtil.verify(inputPassword, storedHash)) {
            throw new RuntimeException("密码错误");
        }

        log.debug("密码验证通过，用户ID: {}", userData.get("student_id") != null ?
                userData.get("student_id") : userData.get("teacher_id"));

        // 生成JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userData.get("student_id") != null ?
                userData.get("student_id") : userData.get("teacher_id"));
        claims.put("role", dto.getRole());
        claims.put("name", userData.get("name"));
        claims.put("ip", clientIp);

        return jwtConfig.generateToken(claims);
    }

    /**
     * 普通用户修改自己的密码
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @param userId 当前用户ID（从Token中获取）
     * @param role 用户角色
     * @param clientIp 客户端IP
     * @return 是否成功
     */
    public boolean changePassword(String oldPassword, String newPassword, String userId, String role, String clientIp) {
        log.info("用户 {} 正在修改密码", userId);

        // 1. 确定表名和主键字段
        String tableName;
        String primaryKeyField;

        switch (role) {
            case "STUDENT":
                tableName = "students";
                primaryKeyField = "student_id";
                break;
            case "TEACHER":
                tableName = "teachers";
                primaryKeyField = "teacher_id";
                break;
            case "ADMIN":
                tableName = "admins";
                primaryKeyField = "admin_id";
                break;
            default:
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

        // 4. 加密新密码并更新
        String newHash = BCryptUtil.hash(newPassword);
        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable(tableName);
        updateDTO.setData(Map.of("password_hash", newHash));
        updateDTO.setConditions(Map.of(primaryKeyField, userId));

        boolean success = manipulationInterface.update(updateDTO);

        // 调用远程审计接口
        if (success) {
            AuditLogDTO auditLog = new AuditLogDTO();
            auditLog.setOperationType("PASSWORD_CHANGE");
            auditLog.setTableName(tableName);
            auditLog.setRecordId(userId);
            auditLog.setOperatorId(userId);
            auditLog.setOperatorType(role);
            auditLog.setClientIp(clientIp);

            auditInterface.logOperation(auditLog);  //  远程调用
            log.info("用户 {} 密码修改成功，审计日志已提交服务端", userId);
        } else {
            log.error("用户 {} 密码修改失败", userId);
        }

        return success;
    }

    /**
     * 管理员重置他人密码（核心功能）
     * @param targetUserId 目标用户ID
     * @param targetRole 目标用户角色
     * @param newPassword 新密码
     * @param adminId 管理员ID
     * @param adminIp 管理员操作IP
     * @return 是否成功
     */
    public boolean adminResetPassword(String targetUserId, String targetRole, String newPassword, String adminId, String adminIp) {
        log.warn("执行管理员密码重置: 目标用户={}, 角色={}, 操作员={}", targetUserId, targetRole, adminId);

        // 1. 确定目标表
        String tableName;
        String primaryKeyField;

        switch (targetRole) {
            case "STUDENT":
                tableName = "students";
                primaryKeyField = "student_id";
                break;
            case "TEACHER":
                tableName = "teachers";
                primaryKeyField = "teacher_id";
                break;
            default:
                throw new RuntimeException("不支持的角色类型: " + targetRole);
        }

        // 2. 验证目标用户是否存在
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable(tableName);
        queryDTO.setConditions(Map.of(primaryKeyField, targetUserId));

        Map<String, Object> userData = selectInterface.select(queryDTO);
        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("目标用户不存在: " + targetUserId);
        }

        // 3. 加密新密码
        String newHash = BCryptUtil.hash(newPassword);

        // 4. 执行更新
        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable(tableName);
        updateDTO.setData(Map.of("password_hash", newHash));
        updateDTO.setConditions(Map.of(primaryKeyField, targetUserId));

        boolean success = manipulationInterface.update(updateDTO);

        if (success) {
            AuditLogDTO auditLog = new AuditLogDTO();
            auditLog.setOperationType("PASSWORD_RESET");
            auditLog.setTableName(tableName);
            auditLog.setRecordId(targetUserId);
            auditLog.setOperatorId(adminId);
            auditLog.setOperatorType("ADMIN");
            auditLog.setClientIp(adminIp);

            auditInterface.logOperation(auditLog);  // ✅ 远程调用
            log.warn("安全审计：管理员 {} 重置用户 {} 密码，日志已提交服务端", adminId, targetUserId);
        }

        return success;
    }


    /**
     * 教师查看成绩列表（分页）
     * @param teacherId 教师ID（从Token获取）
     * @param semester 学期筛选（可选）
     * @param courseId 课程筛选（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @param clientIp 客户端IP
     * @return 包含成绩列表和分页信息
     */
    public Map<String, Object> viewGrades(String teacherId, String semester, String courseId,
                                          int page, int pageSize, String clientIp) {
        log.info("教师 {} 查询成绩列表, 学期: {}, 课程: {}, IP: {}", teacherId, semester, courseId, clientIp);

        // 1. 构建动态查询条件
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("teacher_id", teacherId); // 只能查看自己录入的成绩

        if (semester != null && !semester.trim().isEmpty()) {
            conditions.put("semester", semester);
        }
        if (courseId != null && !courseId.trim().isEmpty()) {
            conditions.put("course_id", courseId);
        }

        // 2. 调用远程查询接口
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> gradeList = selectInterface.selectList(queryDTO);

        // 3. 解密成绩数据（AES解密）
        for (Map<String, Object> record : gradeList) {
            String encryptedScore = (String) record.get("total_score_encrypted");
            if (encryptedScore != null) {
                // 调用AES解密工具
                String decryptedScore = AESUtil.decrypt(encryptedScore);
                record.put("total_score", decryptedScore); // 添加明文字段用于展示
            }
        }

        // 4. 记录审计日志（远程调用）
        AuditLogDTO auditLog = new AuditLogDTO();
        auditLog.setOperationType("GRADE_VIEW");
        auditLog.setTableName("grade_records");
        auditLog.setRecordId("QUERY_LIST"); // 列表查询用特殊标识
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorType("TEACHER");
        auditLog.setClientIp(clientIp);

        auditInterface.logOperation(auditLog);

        // 5. 手动分页（实际生产建议SQL分页）
        int total = gradeList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = gradeList.subList(fromIndex, toIndex);

        // 6. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        return result;
    }
}