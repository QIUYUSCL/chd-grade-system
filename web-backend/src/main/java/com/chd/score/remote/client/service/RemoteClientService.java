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

import java.util.ArrayList;
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
    private final AESUtil aesUtil;

    public RemoteClientService(SelectInterface selectInterface,
                               ManipulationInterface manipulationInterface,
                               AuditInterface auditInterface,
                               JwtConfig jwtConfig,
                               AESUtil aesUtil) {
        this.selectInterface = selectInterface;
        this.manipulationInterface = manipulationInterface;
        this.auditInterface = auditInterface;
        this.jwtConfig = jwtConfig;
        this.aesUtil = aesUtil;
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
                userData.get("student_id") : (userData.get("teacher_id") != null ?
                userData.get("teacher_id") : userData.get("admin_id")));
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

        // 1. 查询成绩记录
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("teacher_id", teacherId);
        if (semester != null && !semester.trim().isEmpty()) {
            conditions.put("semester", semester);
        }
        if (courseId != null && !courseId.trim().isEmpty()) {
            conditions.put("course_id", courseId);
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> gradeList = selectInterface.selectList(queryDTO);

        // ✅ 2. 批量查询关联的学生姓名和课程名称
        for (Map<String, Object> record : gradeList) {
            String studentId = (String) record.get("student_id");
            String cid = (String) record.get("course_id");

            // 查询学生姓名
            OperationDTO studentQuery = new OperationDTO();
            studentQuery.setOperation("SELECT");
            studentQuery.setTable("students");
            studentQuery.setConditions(Map.of("student_id", studentId));
            Map<String, Object> student = selectInterface.select(studentQuery);
            record.put("student_name", student != null ? student.get("name") : "**未知学生**");

            // 查询课程名称
            OperationDTO courseQuery = new OperationDTO();
            courseQuery.setOperation("SELECT");
            courseQuery.setTable("courses");
            courseQuery.setConditions(Map.of("course_id", cid));
            Map<String, Object> course = selectInterface.select(courseQuery);
            record.put("course_name", course != null ? course.get("course_name") : "**未知课程**");
        }

        // 3. 解密成绩
        aesUtil.decryptRecords(gradeList, "total_score_encrypted");

        // 4. 审计日志
        AuditLogDTO auditLog = new AuditLogDTO();
        auditLog.setOperationType("GRADE_VIEW");
        auditLog.setTableName("grade_records");
        auditLog.setRecordId("QUERY_LIST");
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorType("TEACHER");
        auditLog.setClientIp(clientIp);
        auditInterface.logOperation(auditLog);

        // 5. 手动分页
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

    /**
     * 教师录入成绩（完善版）
     * 支持分项成绩录入、自动计算总分、支持补考成绩
     */
    public void entryGrade(Map<String, Object> gradeData, String teacherId, String clientIp) {
        // 1. 参数校验（增强版）
        String studentId = (String) gradeData.get("studentId");
        String courseId = (String) gradeData.get("courseId");
        String semester = (String) gradeData.get("semester");
        String examType = (String) gradeData.get("examType");
        String dailyScore = (String) gradeData.get("dailyScore");
        String finalScore = (String) gradeData.get("finalScore");
        String totalScore = (String) gradeData.get("totalScore");
        String makeupScore = (String) gradeData.get("makeupScore");

        // 校验必填字段
        validateNotEmpty(studentId, "学生ID");
        validateNotEmpty(courseId, "课程ID");
        validateNotEmpty(semester, "学期");
        validateNotEmpty(examType, "考试类型");
        validateNotEmpty(dailyScore, "平时成绩");
        validateNotEmpty(finalScore, "期末成绩");
        validateNotEmpty(totalScore, "总成绩");

        // 校验考试类型
        if (!"正考".equals(examType) && !"补考".equals(examType)) {
            throw new RuntimeException("无效的考试类型: " + examType);
        }

        // 获取前端传入的状态（默认为DRAFT）
        String status = (String) gradeData.get("status");
        if (status == null || status.trim().isEmpty()) {
            status = "DRAFT"; // 默认状态
        }

        if (!"DRAFT".equals(status) && !"SUBMITTED".equals(status)) {
            throw new RuntimeException("无效的状态: " + status);
        }




        // 2. 验证关联数据是否存在
        validateExists("students", "student_id", studentId, "学生");
        validateExists("courses", "course_id", courseId, "课程");

        // 3. 加密各项成绩
        String encryptedDaily = aesUtil.encrypt(dailyScore);
        String encryptedFinal = aesUtil.encrypt(finalScore);
        String encryptedTotal = aesUtil.encrypt(totalScore);
        String encryptedMakeup = null;
        if ("补考".equals(examType) && makeupScore != null) {
            encryptedMakeup = aesUtil.encrypt(makeupScore);
        }

        // 4. 构建插入数据
        Map<String, Object> insertData = new HashMap<>();
        insertData.put("student_id", studentId);
        insertData.put("course_id", courseId);
        insertData.put("teacher_id", teacherId);
        insertData.put("daily_score_encrypted", encryptedDaily);
        insertData.put("final_score_encrypted", encryptedFinal);
        insertData.put("total_score_encrypted", encryptedTotal);
        insertData.put("makeup_score_encrypted", encryptedMakeup);
        insertData.put("exam_type", examType);
        insertData.put("status", status);
        insertData.put("semester", semester);



        // 5. 构建插入DTO
        OperationDTO insertDTO = new OperationDTO();
        insertDTO.setOperation("INSERT");
        insertDTO.setTable("grade_records");
        insertDTO.setData(insertData);

        // 6. 执行插入
        boolean success;
        try {
            success = manipulationInterface.insert(insertDTO);
        } catch (Exception e) {
            log.error("远程插入成绩失败 - 学生: {}, 课程: {}, 异常: {}", studentId, courseId, e.getMessage(), e);
            throw new RuntimeException("成绩录入失败: " + e.getMessage());
        }

        // 7. 记录审计日志
        if (success) {
            AuditLogDTO auditLog = new AuditLogDTO();
            auditLog.setOperationType("GRADE_ENTRY");
            auditLog.setTableName("grade_records");
            auditLog.setRecordId(studentId + "_" + courseId + "_" + examType);
            auditLog.setOperatorId(teacherId);
            auditLog.setOperatorType("TEACHER");
            auditLog.setClientIp(clientIp);

            auditInterface.logOperation(auditLog);
            log.info("教师 {} 录入成绩成功: 学生={}, 课程={}, 类型={}, 总分={}",
                    teacherId, studentId, courseId, examType, totalScore);
        } else {
            throw new RuntimeException("数据库插入失败，返回结果为false");
        }
    }

    // 辅助方法：验证字段非空
    private void validateNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(fieldName + "不能为空");
        }
    }

    // 辅助方法：验证数据存在性
    private void validateExists(String table, String field, String value, String entityName) {
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable(table);
        queryDTO.setConditions(Map.of(field, value));

        Map<String, Object> result = selectInterface.select(queryDTO);
        if (result == null || result.isEmpty()) {
            throw new RuntimeException(entityName + "不存在: " + value);
        }
    }

    /**
     * 获取当前教师教授的课程列表
     * @param teacherId 教师ID
     * @return 课程列表（包含course_id和course_name）
     */
    public List<Map<String, Object>> getTeacherCourses(String teacherId) {
        log.info("教师 {} 查询任课课程列表", teacherId);

        // 构建查询条件
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("courses");
        queryDTO.setConditions(Map.of("teacher_id", teacherId));

        // 调用远程查询接口
        List<Map<String, Object>> courses = selectInterface.selectList(queryDTO);

        log.info("查询到 {} 门课程", courses != null ? courses.size() : 0);
        return courses != null ? courses : new ArrayList<>();
    }


}