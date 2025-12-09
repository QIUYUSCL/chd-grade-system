package com.chd.client.service;

import java.util.*;
import com.chd.client.dto.AuditLogDTO;
import com.chd.client.dto.OperationDTO;
import com.chd.client.utils.AESUtil;
import com.chd.client.utils.BCryptUtil;
import com.chd.client.config.JwtConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException; // [关键] 引入异常类

@Service
public class RemoteClientService {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private AESUtil aesUtil;

    @Value("${remote.server.url}")
    private String serverUrl;

    /**
     * 登录验证
     */
    @SuppressWarnings("unchecked")
    public String executeLogin(OperationDTO dto, String clientIp) {
        dto.setOperation("SELECT");
        Map<String, Object> userData = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", dto, Map.class);

        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        String storedHash = (String) userData.get("password_hash");
        String inputPassword = (String) dto.getData().get("password");

        if (!BCryptUtil.verify(inputPassword, storedHash)) {
            throw new RuntimeException("密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        String userId = (String) (userData.get("student_id") != null ? userData.get("student_id") :
                (userData.get("teacher_id") != null ? userData.get("teacher_id") : userData.get("admin_id")));
        claims.put("userId", userId);
        claims.put("role", dto.getRole());
        claims.put("name", userData.get("name"));

        sendAuditLog("LOGIN", "users", userId, userId, dto.getRole(), clientIp);

        return jwtConfig.generateToken(claims);
    }

    /**
     * 录入成绩
     */
    @SuppressWarnings("unchecked")
    public void entryGrade(Map<String, Object> gradeData, String teacherId, String clientIp) {
        String studentId = (String) gradeData.get("studentId");
        String courseId = (String) gradeData.get("courseId");
        String semester = (String) gradeData.get("semester");
        String examType = (String) gradeData.get("examType");

        // 补考资格校验
        if ("补考".equals(examType)) {
            OperationDTO queryDTO = new OperationDTO();
            queryDTO.setOperation("SELECT");
            queryDTO.setTable("grade_records");
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("student_id", studentId);
            conditions.put("course_id", courseId);
            conditions.put("semester", semester);
            conditions.put("exam_type", "正考");
            queryDTO.setConditions(conditions);

            List<Map<String, Object>> resultList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

            if (resultList == null || resultList.isEmpty()) {
                throw new RuntimeException("该学生本学期无正考记录，无法录入补考成绩！");
            }

            Map<String, Object> regularRecord = resultList.get(0);
            String encryptedTotal = (String) regularRecord.get("total_score_encrypted");
            if (encryptedTotal != null) {
                String totalScoreStr = aesUtil.decrypt(encryptedTotal);
                try {
                    double totalScore = Double.parseDouble(totalScoreStr);
                    if (totalScore >= 60.0) {
                        throw new RuntimeException("该学生正考成绩已及格 (" + totalScore + "分)，不具备补考资格！");
                    }
                } catch (NumberFormatException e) {
                    log.warn("成绩解析异常: " + totalScoreStr);
                }
            }
        }

        String status = (String) gradeData.get("status");
        Map<String, Object> insertData = new HashMap<>();
        insertData.put("student_id", studentId);
        insertData.put("course_id", courseId);
        insertData.put("teacher_id", teacherId);
        insertData.put("semester", semester);
        insertData.put("exam_type", examType);
        insertData.put("status", status != null ? status : "DRAFT");

        encryptAndPut(insertData, "daily_score_encrypted", gradeData.get("dailyScore"));
        encryptAndPut(insertData, "final_score_encrypted", gradeData.get("finalScore"));
        encryptAndPut(insertData, "total_score_encrypted", gradeData.get("totalScore"));
        encryptAndPut(insertData, "makeup_score_encrypted", gradeData.get("makeupScore"));
        encryptAndPut(insertData, "attendance_score_encrypted", gradeData.get("attendanceScore"));
        encryptAndPut(insertData, "homework_score_encrypted", gradeData.get("homeworkScore"));
        encryptAndPut(insertData, "experiment_score_encrypted", gradeData.get("experimentScore"));
        encryptAndPut(insertData, "midterm_score_encrypted", gradeData.get("midtermScore"));

        OperationDTO insertDTO = new OperationDTO();
        insertDTO.setOperation("INSERT");
        insertDTO.setTable("grade_records");
        insertDTO.setData(insertData);

        try {
            // [关键修改] 捕获远程调用异常
            Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/insert", insertDTO, Boolean.class);

            if (Boolean.TRUE.equals(success)) {
                sendAuditLog("GRADE_ENTRY", "grade_records", studentId + "_" + courseId, teacherId, "TEACHER", clientIp);
            } else {
                throw new RuntimeException("服务端插入失败");
            }
        } catch (HttpStatusCodeException e) {
            // 获取服务端返回的错误信息体 (就是那个包含trace的JSON)
            String responseBody = e.getResponseBodyAsString();
            // 检查是否包含关键提示
            if (responseBody.contains("已有成绩记录")) {
                throw new RuntimeException("已有成绩记录");
            }
            // 如果是其他错误，为了美观也可以简化提示
            throw new RuntimeException("录入失败: " + e.getStatusText());
        }
    }

    /**
     * 查看成绩
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> viewGrades(String teacherId, String semester, String courseId, int page, int pageSize, String clientIp) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("teacher_id", teacherId);
        if (semester != null) conditions.put("semester", semester);
        if (courseId != null) conditions.put("course_id", courseId);

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> gradeList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (gradeList == null) gradeList = new ArrayList<>();

        for (Map<String, Object> record : gradeList) {
            decryptAndPut(record, "total_score", "total_score_encrypted");
            decryptAndPut(record, "daily_score", "daily_score_encrypted");
            decryptAndPut(record, "final_score", "final_score_encrypted");
            decryptAndPut(record, "makeup_score", "makeup_score_encrypted");
            decryptAndPut(record, "attendance_score", "attendance_score_encrypted");
            decryptAndPut(record, "homework_score", "homework_score_encrypted");
            decryptAndPut(record, "experiment_score", "experiment_score_encrypted");
            decryptAndPut(record, "midterm_score", "midterm_score_encrypted");

            OperationDTO stuQuery = new OperationDTO();
            stuQuery.setTable("students");
            stuQuery.setConditions(Map.of("student_id", record.get("student_id")));
            Map<String, Object> stu = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", stuQuery, Map.class);
            record.put("student_name", stu != null ? stu.get("name") : "未知");

            OperationDTO courseQuery = new OperationDTO();
            courseQuery.setTable("courses");
            courseQuery.setConditions(Map.of("course_id", record.get("course_id")));
            Map<String, Object> course = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", courseQuery, Map.class);
            record.put("course_name", course != null ? course.get("course_name") : "未知");
        }

        int total = gradeList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : gradeList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);

        sendAuditLog("GRADE_VIEW", "grade_records", "QUERY", teacherId, "TEACHER", clientIp);
        return result;
    }

    /**
     * 更新成绩
     */
    public boolean updateGradeWithEncryption(String recordId, Map<String, Object> plainData, String teacherId, String clientIp) {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("updated_at", "NOW()");
        updateData.put("status", plainData.get("status"));

        encryptAndPut(updateData, "daily_score_encrypted", plainData.get("daily_score"));
        encryptAndPut(updateData, "final_score_encrypted", plainData.get("final_score"));
        encryptAndPut(updateData, "total_score_encrypted", plainData.get("total_score"));
        encryptAndPut(updateData, "makeup_score_encrypted", plainData.get("makeup_score"));
        encryptAndPut(updateData, "attendance_score_encrypted", plainData.get("attendance_score"));
        encryptAndPut(updateData, "homework_score_encrypted", plainData.get("homework_score"));
        encryptAndPut(updateData, "experiment_score_encrypted", plainData.get("experiment_score"));
        encryptAndPut(updateData, "midterm_score_encrypted", plainData.get("midterm_score"));

        OperationDTO dto = new OperationDTO();
        dto.setTable("grade_records");
        dto.setOperation("UPDATE");
        dto.setData(updateData);
        dto.setConditions(Map.of("record_id", recordId));

        try {
            // [关键修改] 捕获远程调用异常
            Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", dto, Boolean.class);

            if (Boolean.TRUE.equals(success)) {
                sendAuditLog("GRADE_UPDATE", "grade_records", recordId, teacherId, "TEACHER", clientIp);
                return true;
            }
            return false;
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            if (body.contains("已有成绩记录")) {
                throw new RuntimeException("已有成绩记录");
            }
            throw new RuntimeException("修改失败");
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTeacherCourses(String teacherId) {
        OperationDTO dto = new OperationDTO();
        dto.setTable("courses");
        dto.setOperation("SELECT");
        dto.setConditions(Map.of("teacher_id", teacherId));
        return (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", dto, List.class);
    }

    public boolean adminResetPassword(String targetUserId, String role, String newPassword, String adminId, String ip) {
        String table = "STUDENT".equals(role) ? "students" : "teachers";
        String idCol = "STUDENT".equals(role) ? "student_id" : "teacher_id";

        String hash = BCryptUtil.hash(newPassword);

        OperationDTO dto = new OperationDTO();
        dto.setTable(table);
        dto.setOperation("UPDATE");
        dto.setData(Map.of("password_hash", hash));
        dto.setConditions(Map.of(idCol, targetUserId));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", dto, Boolean.class);

        if(Boolean.TRUE.equals(success)) {
            sendAuditLog("PASSWORD_RESET", table, targetUserId, adminId, "ADMIN", ip);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean changePassword(String oldPwd, String newPwd, String userId, String role, String ip) {
        String table;
        String idCol;
        if ("STUDENT".equals(role)) {
            table = "students";
            idCol = "student_id";
        } else if ("TEACHER".equals(role)) {
            table = "teachers";
            idCol = "teacher_id";
        } else {
            table = "admins";
            idCol = "admin_id";
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable(table);
        queryDTO.setConditions(Map.of(idCol, userId));

        Map<String, Object> userData = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", queryDTO, Map.class);

        if (userData == null || userData.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }

        String storedHash = (String) userData.get("password_hash");

        if (!BCryptUtil.verify(oldPwd, storedHash)) {
            throw new RuntimeException("旧密码验证失败");
        }

        String newHash = BCryptUtil.hash(newPwd);

        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable(table);
        updateDTO.setData(Map.of("password_hash", newHash));
        updateDTO.setConditions(Map.of(idCol, userId));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", updateDTO, Boolean.class);

        if (Boolean.TRUE.equals(success)) {
            sendAuditLog("PASSWORD_CHANGE", table, userId, userId, role, ip);
            return true;
        }
        return false;
    }

    private void sendAuditLog(String opType, String table, String recordId, String opId, String opTypeRole, String ip) {
        try {
            AuditLogDTO log = new AuditLogDTO();
            log.setOperationType(opType);
            log.setTableName(table);
            log.setRecordId(recordId);
            log.setOperatorId(opId);
            log.setOperatorType(opTypeRole);
            log.setClientIp(ip);
            restTemplate.postForObject(serverUrl + "/audit", log, Boolean.class);
        } catch (Exception e) {
            log.error("审计日志发送失败", e);
        }
    }

    private void encryptAndPut(Map<String, Object> map, String key, Object value) {
        if (value != null && !String.valueOf(value).isEmpty()) {
            map.put(key, aesUtil.encrypt(String.valueOf(value)));
        }
    }

    private void decryptAndPut(Map<String, Object> map, String targetKey, String sourceKey) {
        String encrypted = (String) map.get(sourceKey);
        map.put(targetKey, encrypted != null ? aesUtil.decrypt(encrypted) : "--");
    }

    /**
     * 撤销（删除）成绩记录
     * 逻辑：先查询记录状态，如果是 DRAFT 则删除，否则报错
     */
    @SuppressWarnings("unchecked")
    public void revokeGrade(String recordId, String teacherId, String clientIp) {
        // 1. 先查询该记录，确认是否存在以及状态
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        // 必须同时匹配 recordId 和 teacherId，防止删除别人的成绩
        queryDTO.setConditions(Map.of("record_id", recordId, "teacher_id", teacherId));

        List<Map<String, Object>> list = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("记录不存在或无权操作");
        }

        Map<String, Object> record = list.get(0);
        String status = (String) record.get("status");

        // 2. 核心校验：只有草稿才能撤销
        if ("SUBMITTED".equals(status)) {
            throw new RuntimeException("该成绩已归档锁定，无法撤销！如需修改请联系管理员。");
        }

        // 3. 执行物理删除
        OperationDTO deleteDTO = new OperationDTO();
        deleteDTO.setOperation("DELETE");
        deleteDTO.setTable("grade_records");
        deleteDTO.setConditions(Map.of("record_id", recordId));

        try {
            Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDTO, Boolean.class);
            if (Boolean.TRUE.equals(success)) {
                sendAuditLog("GRADE_REVOKE", "grade_records", recordId, teacherId, "TEACHER", clientIp);
            } else {
                throw new RuntimeException("服务端删除失败");
            }
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("撤销失败: " + e.getResponseBodyAsString());
        }
    }
}