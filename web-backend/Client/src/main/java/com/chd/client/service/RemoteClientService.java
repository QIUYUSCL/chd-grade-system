package com.chd.client.service;

import java.util.*;
import java.util.stream.Collectors;
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
    public Map<String, Object> viewGrades(String teacherId, String semester, String courseId, String examType, String status, String className, int page, int pageSize, String clientIp) {

        // 1. 构建成绩查询条件
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("teacher_id", teacherId);
        if (semester != null && !semester.isEmpty()) conditions.put("semester", semester);
        if (courseId != null && !courseId.isEmpty()) conditions.put("course_id", courseId);
        if (examType != null && !examType.isEmpty()) conditions.put("exam_type", examType);
        if (status != null && !status.isEmpty()) conditions.put("status", status);

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        // RPC: 获取原始成绩列表
        List<Map<String, Object>> rawList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (rawList == null) rawList = new ArrayList<>();

        // 2. [优化] 批量获取学生信息 (解决循环查库 N+1 问题)
        Set<String> studentIds = rawList.stream()
                .map(r -> (String) r.get("student_id"))
                .collect(Collectors.toSet());

        Map<String, Map<String, Object>> studentMap = new HashMap<>();
        if (!studentIds.isEmpty()) {
            OperationDTO stuDTO = new OperationDTO();
            stuDTO.setOperation("SELECT");
            stuDTO.setTable("students");
            // 使用 student_id_in 进行批量查询
            Map<String, Object> stuCond = new HashMap<>();
            stuCond.put("student_id_in", new ArrayList<>(studentIds));
            stuDTO.setConditions(stuCond);

            List<Map<String, Object>> students = (List<Map<String, Object>>) restTemplate.postForObject(
                    serverUrl + "/selectList", stuDTO, List.class);

            if (students != null) {
                for (Map<String, Object> s : students) {
                    studentMap.put((String) s.get("student_id"), s);
                }
            }
        }

        // 3. [优化] 批量/单次获取课程信息
        // 如果查询指定了 courseId，只查一次课程信息即可，不用在循环里查
        Map<String, Object> currentCourseInfo = null;
        if (courseId != null && !courseId.isEmpty()) {
            OperationDTO courseQuery = new OperationDTO();
            courseQuery.setTable("courses");
            courseQuery.setConditions(Map.of("course_id", courseId));
            currentCourseInfo = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", courseQuery, Map.class);
        }

        // 4. 遍历、解密、关联信息、执行过滤
        List<Map<String, Object>> filteredList = new ArrayList<>();

        for (Map<String, Object> record : rawList) {
            String stuId = (String) record.get("student_id");
            Map<String, Object> stuInfo = studentMap.get(stuId);

            String stuName = stuInfo != null ? (String) stuInfo.get("name") : "未知";
            String stuClass = stuInfo != null ? (String) stuInfo.get("class_name") : ""; // 获取班级

            // ✅✅✅ [核心] 班级过滤
            if (className != null && !className.isEmpty()) {
                // 如果前端传了班级，且当前学生班级不匹配，则跳过
                if (!className.equals(stuClass)) {
                    continue;
                }
            }

            // 解密分数
            decryptAndPut(record, "total_score", "total_score_encrypted");
            decryptAndPut(record, "daily_score", "daily_score_encrypted");
            decryptAndPut(record, "final_score", "final_score_encrypted");
            decryptAndPut(record, "makeup_score", "makeup_score_encrypted");
            decryptAndPut(record, "attendance_score", "attendance_score_encrypted");
            decryptAndPut(record, "homework_score", "homework_score_encrypted");
            decryptAndPut(record, "experiment_score", "experiment_score_encrypted");
            decryptAndPut(record, "midterm_score", "midterm_score_encrypted");

            // 填充学生信息
            record.put("student_name", stuName);
            record.put("class_name", stuClass); // 将班级返回给前端显示

            // 填充课程信息
            if (currentCourseInfo != null) {
                record.put("course_name", currentCourseInfo.get("course_name"));
                record.put("credit", currentCourseInfo.get("credit"));
            } else {
                // 如果没选课程(查询全部)，则需要单独查 (或者也可以像学生一样做批量优化)
                // 为保持兼容暂保留原逻辑
                OperationDTO cq = new OperationDTO();
                cq.setTable("courses");
                cq.setConditions(Map.of("course_id", record.get("course_id")));
                Map<String, Object> c = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", cq, Map.class);
                record.put("course_name", c != null ? c.get("course_name") : "未知");
                record.put("credit", (c != null && c.get("credit") != null) ? c.get("credit") : 3.0);
            }

            filteredList.add(record);
        }

        // 5. 内存分页 (因为经过过滤，数据库的分页已经不准确了)
        int total = filteredList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<Map<String, Object>> pageList = new ArrayList<>();
        if (fromIndex < total) {
            pageList = filteredList.subList(fromIndex, toIndex);
        }

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

    /**
     * 根据学号查询姓名
     */
    @SuppressWarnings("unchecked")
    public String getStudentName(String studentId) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("students");
        dto.setConditions(Map.of("student_id", studentId));

        Map<String, Object> res = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", dto, Map.class);

        return (res != null && !res.isEmpty()) ? (String) res.get("name") : "未找到该学生";
    }

    /**
     * 根据课程和学期查询选课学生
     * 逻辑：先查 student_courses 表得到所有 student_id，再循环查 students 表得到详情
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStudentsByCourse(String courseId, String semester) {
        // 1. 查询选课关系
        OperationDTO scQuery = new OperationDTO();
        scQuery.setOperation("SELECT");
        scQuery.setTable("student_courses");
        scQuery.setConditions(Map.of("course_id", courseId, "semester", semester));

        List<Map<String, Object>> relationList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", scQuery, List.class);

        if (relationList == null || relationList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> studentList = new ArrayList<>();

        // 2. 循环获取学生详情 (为了复用现有接口，暂不使用复杂SQL)
        for (Map<String, Object> rel : relationList) {
            String stuId = (String) rel.get("student_id");

            OperationDTO stuQuery = new OperationDTO();
            stuQuery.setOperation("SELECT");
            stuQuery.setTable("students");
            stuQuery.setConditions(Map.of("student_id", stuId));

            Map<String, Object> student = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", stuQuery, Map.class);
            if (student != null) {
                // 仅保留必要字段
                Map<String, Object> info = new HashMap<>();
                info.put("student_id", student.get("student_id"));
                info.put("name", student.get("name"));
                info.put("class_name", student.get("class_name"));
                studentList.add(info);
            }
        }

        return studentList;
    }

    /**
     * 批量录入
     */
    public void batchEntryGrade(List<Map<String, Object>> grades, String teacherId, String clientIp) {
        if (grades == null || grades.isEmpty()) return;

        for (Map<String, Object> grade : grades) {
            try {
                // 复用单条录入逻辑 (包含加密、审计、补考校验等所有逻辑)
                entryGrade(grade, teacherId, clientIp);
            } catch (Exception e) {
                String sid = (String) grade.get("studentId");
                throw new RuntimeException("学生[" + sid + "]录入失败: " + e.getMessage());
            }
        }
    }

    /**
     * 计算成绩统计指标
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calculateGradeStats(String courseId, String semester, String examType) {
        // 1. 获取应考人数
        List<Map<String, Object>> students = getStudentsByCourse(courseId, semester);
        int totalStudents = students.size();

        // 2. 获取实考成绩记录
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("course_id", courseId);
        conditions.put("semester", semester);
        conditions.put("status", "SUBMITTED"); // 只统计已归档
        if (examType != null && !examType.isEmpty()) {
            conditions.put("exam_type", examType);
        }
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> records = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);
        if (records == null) records = new ArrayList<>();

        if ("补考".equals(examType)) {
            totalStudents = records.size();
        }

        // 3. 统计指标计算
        int realStudents = 0;
        double sumScore = 0;
        double maxScore = 0;
        double minScore = 100;
        int passCount = 0;
        int[] distribution = new int[5];

        // [新增] 用于存储所有具体分数，供折线图使用
        List<Double> scoreList = new ArrayList<>();

        for (Map<String, Object> record : records) {
            String encTotal = (String) record.get("total_score_encrypted");
            if (encTotal == null) continue;

            try {
                double score = Double.parseDouble(aesUtil.decrypt(encTotal));

                // [新增] 收集分数
                scoreList.add(score);

                realStudents++;
                sumScore += score;

                if (score > maxScore) maxScore = score;
                if (score < minScore) minScore = score;
                if (score >= 60) passCount++;

                if (score < 60) distribution[0]++;
                else if (score < 70) distribution[1]++;
                else if (score < 80) distribution[2]++;
                else if (score < 90) distribution[3]++;
                else distribution[4]++;

            } catch (Exception e) {
                log.warn("Record {} score parse failed", record.get("record_id"));
            }
        }

        if (realStudents == 0) minScore = 0;

        // 对分数进行排序
        Collections.sort(scoreList, Collections.reverseOrder());

        // 4. 封装结果
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudents", totalStudents);
        stats.put("realStudents", realStudents);
        stats.put("maxScore", maxScore);
        stats.put("minScore", minScore);
        stats.put("avgScore", realStudents > 0 ? String.format("%.2f", sumScore / realStudents) : "0.00");
        stats.put("passRate", realStudents > 0 ? String.format("%.2f", (double)passCount * 100 / realStudents) : "0.00");
        stats.put("distribution", distribution);
        // [新增] 返回分数列表
        stats.put("scoreList", scoreList);

        return stats;
    }


    public Map<String, Object> getTeacherInfo(String teacherId) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("teachers"); // 指定查询教师表
        dto.setConditions(Map.of("teacher_id", teacherId)); // WHERE teacher_id = ?

        // 发送 RPC 请求给 Server
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) restTemplate.postForObject(
                serverUrl + "/select", dto, Map.class);

        return result;
    }

    /**
     * 保存分析报告 (存在则更新，不存在则插入)
     * 使用 MySQL 的 ON DUPLICATE KEY UPDATE 逻辑，或者先查后改
     * 这里为了复用简单的 Insert/Update 接口，采用“先查后改”策略
     */
    @SuppressWarnings("unchecked")
    public void saveAnalysis(Map<String, Object> data, String teacherId, String clientIp) {
        String courseId = (String) data.get("courseId");
        String semester = (String) data.get("semester");

        // 1. 检查是否已存在
        Map<String, Object> existing = getAnalysis(courseId, semester, teacherId);

        // 准备数据
        Map<String, Object> dbData = new HashMap<>();
        dbData.put("course_id", courseId);
        dbData.put("semester", semester);
        dbData.put("teacher_id", teacherId);
        dbData.put("avg_score", data.get("avgScore"));
        dbData.put("pass_rate", data.get("passRate"));
        dbData.put("max_score", data.get("maxScore"));
        dbData.put("min_score", data.get("minScore"));
        // 将数组转为JSON字符串存储 (简单处理)
        dbData.put("distribution_json", data.get("distributionJson").toString());
        dbData.put("analysis_content", data.get("content"));

        OperationDTO dto = new OperationDTO();
        dto.setTable("grade_analysis");

        if (existing != null) {
            // 更新
            dto.setOperation("UPDATE");
            dto.setData(dbData);
            // 修正：Update 需要 Where 条件
            dto.setConditions(Map.of("id", existing.get("id")));
            restTemplate.postForObject(serverUrl + "/manipulate/update", dto, Boolean.class);
        } else {
            // 插入
            dto.setOperation("INSERT");
            dto.setData(dbData);
            restTemplate.postForObject(serverUrl + "/manipulate/insert", dto, Boolean.class);
        }

        // 记录日志
        sendAuditLog("ANALYSIS_SAVE", "grade_analysis", courseId + "_" + semester, teacherId, "TEACHER", clientIp);
    }

    /**
     * 查询分析报告
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnalysis(String courseId, String semester, String teacherId) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("grade_analysis");
        dto.setConditions(Map.of(
                "course_id", courseId,
                "semester", semester,
                "teacher_id", teacherId
        ));

        List<Map<String, Object>> list = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", dto, List.class);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    // ==================== 管理员功能实现 ====================

    /**
     * 管理员查询成绩记录
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminQueryGrades(Map<String, Object> params, String adminId, String clientIp) {
        // 构建查询条件
        Map<String, Object> conditions = new HashMap<>();
        
        if (params.get("studentId") != null && !String.valueOf(params.get("studentId")).isEmpty()) {
            conditions.put("student_id", params.get("studentId"));
        }
        if (params.get("courseId") != null && !String.valueOf(params.get("courseId")).isEmpty()) {
            conditions.put("course_id", params.get("courseId"));
        }
        if (params.get("teacherId") != null && !String.valueOf(params.get("teacherId")).isEmpty()) {
            conditions.put("teacher_id", params.get("teacherId"));
        }
        if (params.get("semester") != null && !String.valueOf(params.get("semester")).isEmpty()) {
            conditions.put("semester", params.get("semester"));
        }
        if (params.get("examType") != null && !String.valueOf(params.get("examType")).isEmpty()) {
            conditions.put("exam_type", params.get("examType"));
        }
        if (params.get("status") != null && !String.valueOf(params.get("status")).isEmpty()) {
            conditions.put("status", params.get("status"));
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> gradeList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (gradeList == null) gradeList = new ArrayList<>();

        // 解密成绩并补充关联信息
        for (Map<String, Object> record : gradeList) {
            // 解密所有成绩字段
            decryptAndPut(record, "total_score", "total_score_encrypted");
            decryptAndPut(record, "daily_score", "daily_score_encrypted");
            decryptAndPut(record, "final_score", "final_score_encrypted");
            decryptAndPut(record, "makeup_score", "makeup_score_encrypted");
            decryptAndPut(record, "attendance_score", "attendance_score_encrypted");
            decryptAndPut(record, "homework_score", "homework_score_encrypted");
            decryptAndPut(record, "experiment_score", "experiment_score_encrypted");
            decryptAndPut(record, "midterm_score", "midterm_score_encrypted");

            // 获取学生信息
            OperationDTO stuQuery = new OperationDTO();
            stuQuery.setTable("students");
            stuQuery.setConditions(Map.of("student_id", record.get("student_id")));
            Map<String, Object> stu = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", stuQuery, Map.class);
            record.put("student_name", stu != null ? stu.get("name") : "未知");
            record.put("class_name", stu != null ? stu.get("class_name") : "未知");

            // 获取课程信息
            OperationDTO courseQuery = new OperationDTO();
            courseQuery.setTable("courses");
            courseQuery.setConditions(Map.of("course_id", record.get("course_id")));
            Map<String, Object> course = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", courseQuery, Map.class);
            record.put("course_name", course != null ? course.get("course_name") : "未知");

            // 获取教师信息
            OperationDTO teacherQuery = new OperationDTO();
            teacherQuery.setTable("teachers");
            teacherQuery.setConditions(Map.of("teacher_id", record.get("teacher_id")));
            Map<String, Object> teacher = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", teacherQuery, Map.class);
            record.put("teacher_name", teacher != null ? teacher.get("name") : "未知");
        }

        // 分页处理
        int page = (Integer) params.getOrDefault("page", 1);
        int pageSize = (Integer) params.getOrDefault("pageSize", 20);
        int total = gradeList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : gradeList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        // 记录审计日志
        sendAuditLog("ADMIN_GRADE_QUERY", "grade_records", "QUERY", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 管理员小撤销：将已提交成绩改为草稿状态
     */
    @SuppressWarnings("unchecked")
    public void adminMinorRevoke(String recordId, String adminId, String clientIp) {
        // 1. 先查询记录是否存在
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(Map.of("record_id", recordId));

        List<Map<String, Object>> list = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("成绩记录不存在");
        }

        Map<String, Object> record = list.get(0);
        String currentStatus = (String) record.get("status");

        if (!"SUBMITTED".equals(currentStatus)) {
            throw new RuntimeException("只能撤销已提交的成绩记录");
        }

        // 2. 更新状态为草稿
        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable("grade_records");
        updateDTO.setData(Map.of("status", "DRAFT", "updated_at", "NOW()"));
        updateDTO.setConditions(Map.of("record_id", recordId));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", updateDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("小撤销操作失败");
        }

        // 3. 记录审计日志
        String logRecordId = record.get("student_id") + "_" + record.get("course_id") + "_" + record.get("exam_type");
        sendAuditLog("ADMIN_MINOR_REVOKE", "grade_records", logRecordId, adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员大撤销：完全删除成绩记录
     */
    @SuppressWarnings("unchecked")
    public void adminMajorRevoke(String recordId, String adminId, String clientIp) {
        // 1. 先查询记录是否存在（用于日志记录）
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(Map.of("record_id", recordId));

        List<Map<String, Object>> list = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("成绩记录不存在");
        }

        Map<String, Object> record = list.get(0);

        // 2. 执行删除操作
        OperationDTO deleteDTO = new OperationDTO();
        deleteDTO.setOperation("DELETE");
        deleteDTO.setTable("grade_records");
        deleteDTO.setConditions(Map.of("record_id", recordId));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("大撤销操作失败");
        }

        // 3. 记录审计日志
        String logRecordId = record.get("student_id") + "_" + record.get("course_id") + "_" + record.get("exam_type");
        sendAuditLog("ADMIN_MAJOR_REVOKE", "grade_records", logRecordId, adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员查询用户列表
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminQueryUsers(Map<String, Object> params, String adminId, String clientIp) {
        String userType = (String) params.get("userType"); // STUDENT, TEACHER, ADMIN
        String table;
        String idField;

        switch (userType) {
            case "STUDENT":
                table = "students";
                idField = "student_id";
                break;
            case "TEACHER":
                table = "teachers";
                idField = "teacher_id";
                break;
            case "ADMIN":
                table = "admins";
                idField = "admin_id";
                break;
            default:
                throw new RuntimeException("无效的用户类型");
        }

        // 构建查询条件
        Map<String, Object> conditions = new HashMap<>();
        if (params.get("userId") != null && !String.valueOf(params.get("userId")).isEmpty()) {
            conditions.put(idField, params.get("userId"));
        }
        if (params.get("name") != null && !String.valueOf(params.get("name")).isEmpty()) {
            // 这里简化处理，实际可能需要模糊查询
            conditions.put("name", params.get("name"));
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable(table);
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> userList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (userList == null) userList = new ArrayList<>();

        // 移除密码哈希字段
        for (Map<String, Object> user : userList) {
            user.remove("password_hash");
        }

        // 分页处理
        int page = (Integer) params.getOrDefault("page", 1);
        int pageSize = (Integer) params.getOrDefault("pageSize", 20);
        int total = userList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : userList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        // 记录审计日志
        sendAuditLog("ADMIN_USER_QUERY", table, "QUERY", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 管理员添加用户
     */
    public void adminAddUser(Map<String, Object> params, String adminId, String clientIp) {
        String userType = (String) params.get("userType");
        String table;
        String idField;

        switch (userType) {
            case "STUDENT":
                table = "students";
                idField = "student_id";
                break;
            case "TEACHER":
                table = "teachers";
                idField = "teacher_id";
                break;
            case "ADMIN":
                table = "admins";
                idField = "admin_id";
                break;
            default:
                throw new RuntimeException("无效的用户类型");
        }

        // 准备插入数据
        Map<String, Object> userData = new HashMap<>();
        userData.put(idField, params.get("userId"));
        userData.put("name", params.get("name"));
        userData.put("password_hash", BCryptUtil.hash((String) params.get("password")));
        userData.put("email", params.get("email"));

        // 根据用户类型添加特定字段
        if ("STUDENT".equals(userType)) {
            userData.put("class_name", params.get("className"));
            userData.put("major", params.get("major"));
        } else if ("TEACHER".equals(userType)) {
            userData.put("department", params.get("department"));
            userData.put("title", params.get("title"));
        } else if ("ADMIN".equals(userType)) {
            userData.put("department", params.get("department"));
        }

        OperationDTO insertDTO = new OperationDTO();
        insertDTO.setOperation("INSERT");
        insertDTO.setTable(table);
        insertDTO.setData(userData);

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/insert", insertDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("用户添加失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_USER_ADD", table, (String) params.get("userId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员更新用户信息
     */
    public void adminUpdateUser(Map<String, Object> params, String adminId, String clientIp) {
        String userType = (String) params.get("userType");
        String table;
        String idField;

        switch (userType) {
            case "STUDENT":
                table = "students";
                idField = "student_id";
                break;
            case "TEACHER":
                table = "teachers";
                idField = "teacher_id";
                break;
            case "ADMIN":
                table = "admins";
                idField = "admin_id";
                break;
            default:
                throw new RuntimeException("无效的用户类型");
        }

        // 准备更新数据
        Map<String, Object> updateData = new HashMap<>();
        if (params.get("name") != null) {
            updateData.put("name", params.get("name"));
        }
        if (params.get("email") != null) {
            updateData.put("email", params.get("email"));
        }

        // 根据用户类型更新特定字段
        if ("STUDENT".equals(userType)) {
            if (params.get("className") != null) {
                updateData.put("class_name", params.get("className"));
            }
            if (params.get("major") != null) {
                updateData.put("major", params.get("major"));
            }
        } else if ("TEACHER".equals(userType)) {
            if (params.get("department") != null) {
                updateData.put("department", params.get("department"));
            }
            if (params.get("title") != null) {
                updateData.put("title", params.get("title"));
            }
        } else if ("ADMIN".equals(userType)) {
            if (params.get("department") != null) {
                updateData.put("department", params.get("department"));
            }
        }

        if (updateData.isEmpty()) {
            throw new RuntimeException("没有需要更新的数据");
        }

        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable(table);
        updateDTO.setData(updateData);
        updateDTO.setConditions(Map.of(idField, params.get("userId")));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", updateDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("用户信息更新失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_USER_UPDATE", table, (String) params.get("userId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员删除用户
     */
    public void adminDeleteUser(Map<String, Object> params, String adminId, String clientIp) {
        String userType = (String) params.get("userType");
        String table;
        String idField;

        switch (userType) {
            case "STUDENT":
                table = "students";
                idField = "student_id";
                break;
            case "TEACHER":
                table = "teachers";
                idField = "teacher_id";
                break;
            case "ADMIN":
                table = "admins";
                idField = "admin_id";
                break;
            default:
                throw new RuntimeException("无效的用户类型");
        }

        OperationDTO deleteDTO = new OperationDTO();
        deleteDTO.setOperation("DELETE");
        deleteDTO.setTable(table);
        deleteDTO.setConditions(Map.of(idField, params.get("userId")));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("用户删除失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_USER_DELETE", table, (String) params.get("userId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员查询安全日志
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminQuerySecurityLog(Map<String, Object> params, String adminId, String clientIp) {
        // 构建查询条件
        Map<String, Object> conditions = new HashMap<>();
        
        if (params.get("operationType") != null && !String.valueOf(params.get("operationType")).isEmpty()) {
            conditions.put("operation_type", params.get("operationType"));
        }
        if (params.get("tableName") != null && !String.valueOf(params.get("tableName")).isEmpty()) {
            conditions.put("table_name", params.get("tableName"));
        }
        if (params.get("operatorId") != null && !String.valueOf(params.get("operatorId")).isEmpty()) {
            conditions.put("operator_id", params.get("operatorId"));
        }
        if (params.get("operatorType") != null && !String.valueOf(params.get("operatorType")).isEmpty()) {
            conditions.put("operator_type", params.get("operatorType"));
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("security_log");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> logList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (logList == null) logList = new ArrayList<>();

        // 分页处理
        int page = (Integer) params.getOrDefault("page", 1);
        int pageSize = (Integer) params.getOrDefault("pageSize", 50);
        int total = logList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : logList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        // 记录审计日志
        sendAuditLog("ADMIN_LOG_QUERY", "security_log", "QUERY", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 管理员数据完整性校验
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminVerifyDataIntegrity(String adminId, String clientIp) {
        // 查询所有成绩记录进行哈希校验
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(new HashMap<>());

        List<Map<String, Object>> records = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (records == null) records = new ArrayList<>();

        List<Map<String, Object>> tamperedRecords = new ArrayList<>();
        int totalRecords = records.size();
        int verifiedRecords = 0;

        for (Map<String, Object> record : records) {
            String recordId = String.valueOf(record.get("record_id"));
            String studentId = (String) record.get("student_id");
            String courseId = (String) record.get("course_id");
            String examType = (String) record.get("exam_type");
            String totalScoreEncrypted = (String) record.get("total_score_encrypted");
            String storedHash = (String) record.get("data_hash");

            // 重新计算哈希值
            String dataToHash = studentId + courseId + examType + (totalScoreEncrypted != null ? totalScoreEncrypted : "");
            String computedHash = computeSHA256Hash(dataToHash);

            if (storedHash != null && !storedHash.equals(computedHash)) {
                // 发现数据被篡改
                Map<String, Object> tamperedRecord = new HashMap<>();
                tamperedRecord.put("record_id", recordId);
                tamperedRecord.put("student_id", studentId);
                tamperedRecord.put("course_id", courseId);
                tamperedRecord.put("exam_type", examType);
                tamperedRecord.put("stored_hash", storedHash);
                tamperedRecord.put("computed_hash", computedHash);
                tamperedRecords.add(tamperedRecord);
            } else {
                verifiedRecords++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_records", totalRecords);
        result.put("verified_records", verifiedRecords);
        result.put("tampered_records", tamperedRecords.size());
        result.put("tampered_list", tamperedRecords);
        result.put("integrity_status", tamperedRecords.isEmpty() ? "INTACT" : "COMPROMISED");

        // 记录审计日志
        sendAuditLog("ADMIN_DATA_VERIFY", "grade_records", "INTEGRITY_CHECK", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 计算SHA256哈希值
     */
    private String computeSHA256Hash(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("计算哈希值失败", e);
            return "";
        }
    }

    // ==================== 课程管理功能实现 ====================

    /**
     * 管理员查询课程列表
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminQueryCourses(Map<String, Object> params, String adminId, String clientIp) {
        // 构建查询条件
        Map<String, Object> conditions = new HashMap<>();
        
        if (params.get("courseId") != null && !String.valueOf(params.get("courseId")).isEmpty()) {
            conditions.put("course_id", params.get("courseId"));
        }
        if (params.get("courseName") != null && !String.valueOf(params.get("courseName")).isEmpty()) {
            conditions.put("course_name", params.get("courseName"));
        }
        if (params.get("teacherId") != null && !String.valueOf(params.get("teacherId")).isEmpty()) {
            conditions.put("teacher_id", params.get("teacherId"));
        }
        if (params.get("semester") != null && !String.valueOf(params.get("semester")).isEmpty()) {
            conditions.put("semester", params.get("semester"));
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("courses");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> courseList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (courseList == null) courseList = new ArrayList<>();

        // 补充教师姓名信息
        for (Map<String, Object> course : courseList) {
            String teacherId = (String) course.get("teacher_id");
            if (teacherId != null) {
                OperationDTO teacherQuery = new OperationDTO();
                teacherQuery.setTable("teachers");
                teacherQuery.setConditions(Map.of("teacher_id", teacherId));
                Map<String, Object> teacher = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", teacherQuery, Map.class);
                course.put("teacher_name", teacher != null ? teacher.get("name") : "未知");
            } else {
                course.put("teacher_name", "未分配");
            }
        }

        // 分页处理
        int page = (Integer) params.getOrDefault("page", 1);
        int pageSize = (Integer) params.getOrDefault("pageSize", 20);
        int total = courseList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : courseList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        // 记录审计日志
        sendAuditLog("ADMIN_COURSE_QUERY", "courses", "QUERY", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 管理员添加课程
     */
    public void adminAddCourse(Map<String, Object> params, String adminId, String clientIp) {
        // 准备插入数据
        Map<String, Object> courseData = new HashMap<>();
        courseData.put("course_id", params.get("courseId"));
        courseData.put("course_name", params.get("courseName"));
        courseData.put("credit", params.get("credit"));
        courseData.put("department", params.get("department"));
        courseData.put("teacher_id", params.get("teacherId"));
        courseData.put("semester", params.get("semester"));
        
        // 添加新字段支持
        if (params.get("courseType") != null) {
            courseData.put("course_type", params.get("courseType"));
        }
        if (params.get("description") != null) {
            courseData.put("description", params.get("description"));
        }

        OperationDTO insertDTO = new OperationDTO();
        insertDTO.setOperation("INSERT");
        insertDTO.setTable("courses");
        insertDTO.setData(courseData);

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/insert", insertDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("课程添加失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_COURSE_ADD", "courses", (String) params.get("courseId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员更新课程信息
     */
    public void adminUpdateCourse(Map<String, Object> params, String adminId, String clientIp) {
        // 准备更新数据
        Map<String, Object> updateData = new HashMap<>();
        if (params.get("courseName") != null) {
            updateData.put("course_name", params.get("courseName"));
        }
        if (params.get("credit") != null) {
            updateData.put("credit", params.get("credit"));
        }
        if (params.get("department") != null) {
            updateData.put("department", params.get("department"));
        }
        if (params.get("teacherId") != null) {
            updateData.put("teacher_id", params.get("teacherId"));
        }
        if (params.get("semester") != null) {
            updateData.put("semester", params.get("semester"));
        }
        if (params.get("courseType") != null) {
            updateData.put("course_type", params.get("courseType"));
        }
        if (params.get("description") != null) {
            updateData.put("description", params.get("description"));
        }

        if (updateData.isEmpty()) {
            throw new RuntimeException("没有需要更新的数据");
        }

        OperationDTO updateDTO = new OperationDTO();
        updateDTO.setOperation("UPDATE");
        updateDTO.setTable("courses");
        updateDTO.setData(updateData);
        updateDTO.setConditions(Map.of("course_id", params.get("courseId")));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/update", updateDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("课程信息更新失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_COURSE_UPDATE", "courses", (String) params.get("courseId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员删除课程
     */
    public void adminDeleteCourse(Map<String, Object> params, String adminId, String clientIp) {
        OperationDTO deleteDTO = new OperationDTO();
        deleteDTO.setOperation("DELETE");
        deleteDTO.setTable("courses");
        deleteDTO.setConditions(Map.of("course_id", params.get("courseId")));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("课程删除失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_COURSE_DELETE", "courses", (String) params.get("courseId"), adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员查询选课关系
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminQueryStudentCourses(Map<String, Object> params, String adminId, String clientIp) {
        // 构建查询条件
        Map<String, Object> conditions = new HashMap<>();
        
        if (params.get("studentId") != null && !String.valueOf(params.get("studentId")).isEmpty()) {
            conditions.put("student_id", params.get("studentId"));
        }
        if (params.get("courseId") != null && !String.valueOf(params.get("courseId")).isEmpty()) {
            conditions.put("course_id", params.get("courseId"));
        }
        if (params.get("semester") != null && !String.valueOf(params.get("semester")).isEmpty()) {
            conditions.put("semester", params.get("semester"));
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("student_courses");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> scList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);

        if (scList == null) scList = new ArrayList<>();

        // 补充学生和课程信息
        for (Map<String, Object> sc : scList) {
            // 获取学生信息
            OperationDTO stuQuery = new OperationDTO();
            stuQuery.setTable("students");
            stuQuery.setConditions(Map.of("student_id", sc.get("student_id")));
            Map<String, Object> student = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", stuQuery, Map.class);
            sc.put("student_name", student != null ? student.get("name") : "未知");
            sc.put("class_name", student != null ? student.get("class_name") : "未知");

            // 获取课程信息
            OperationDTO courseQuery = new OperationDTO();
            courseQuery.setTable("courses");
            courseQuery.setConditions(Map.of("course_id", sc.get("course_id")));
            Map<String, Object> course = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", courseQuery, Map.class);
            sc.put("course_name", course != null ? course.get("course_name") : "未知");
        }

        // 分页处理
        int page = (Integer) params.getOrDefault("page", 1);
        int pageSize = (Integer) params.getOrDefault("pageSize", 20);
        int total = scList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = (fromIndex > total) ? new ArrayList<>() : scList.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        // 记录审计日志
        sendAuditLog("ADMIN_SC_QUERY", "student_courses", "QUERY", adminId, "ADMIN", clientIp);
        return result;
    }

    /**
     * 管理员添加选课关系
     */
    public void adminAddStudentCourse(Map<String, Object> params, String adminId, String clientIp) {
        // 准备插入数据
        Map<String, Object> scData = new HashMap<>();
        scData.put("student_id", params.get("studentId"));
        scData.put("course_id", params.get("courseId"));
        scData.put("semester", params.get("semester"));

        OperationDTO insertDTO = new OperationDTO();
        insertDTO.setOperation("INSERT");
        insertDTO.setTable("student_courses");
        insertDTO.setData(scData);

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/insert", insertDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("选课关系添加失败");
        }

        // 记录审计日志
        String recordId = params.get("studentId") + "_" + params.get("courseId") + "_" + params.get("semester");
        sendAuditLog("ADMIN_SC_ADD", "student_courses", recordId, adminId, "ADMIN", clientIp);
    }

    /**
     * 管理员删除选课关系
     */
    public void adminDeleteStudentCourse(Map<String, Object> params, String adminId, String clientIp) {
        OperationDTO deleteDTO = new OperationDTO();
        deleteDTO.setOperation("DELETE");
        deleteDTO.setTable("student_courses");
        deleteDTO.setConditions(Map.of("id", params.get("id")));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDTO, Boolean.class);

        if (!Boolean.TRUE.equals(success)) {
            throw new RuntimeException("选课关系删除失败");
        }

        // 记录审计日志
        sendAuditLog("ADMIN_SC_DELETE", "student_courses", String.valueOf(params.get("id")), adminId, "ADMIN", clientIp);
    }

    /**
     * 获取教师列表（用于课程管理下拉选择）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTeachersList() {
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("teachers");
        queryDTO.setConditions(new HashMap<>());

        List<Map<String, Object>> teacherList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);
        
        if (teacherList == null) {
            teacherList = new ArrayList<>();
        }

        // 只返回需要的字段
        return teacherList.stream()
                .map(teacher -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("teacher_id", teacher.get("teacher_id"));
                    result.put("name", teacher.get("name"));
                    result.put("department", teacher.get("department"));
                    result.put("title", teacher.get("title"));
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取院系列表（用于课程管理下拉选择）
     */
    @SuppressWarnings("unchecked")
    public List<String> getDepartmentsList() {
        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("teachers");
        queryDTO.setConditions(new HashMap<>());

        List<Map<String, Object>> teacherList = (List<Map<String, Object>>) restTemplate.postForObject(serverUrl + "/selectList", queryDTO, List.class);
        
        if (teacherList == null) {
            return new ArrayList<>();
        }

        // 提取院系并去重
        return teacherList.stream()
                .map(teacher -> (String) teacher.get("department"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    // ==================== 学生选课功能 ====================

    /**
     * 获取选课列表（含状态和过滤逻辑）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStudentCourseSelectionList(String studentId, String currentSemester) {
        // 1. 获取本学期开设的所有课程
        OperationDTO courseQuery = new OperationDTO();
        courseQuery.setOperation("SELECT");
        courseQuery.setTable("courses");
        courseQuery.setConditions(Map.of("semester", currentSemester));
        List<Map<String, Object>> currentSemesterCourses = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", courseQuery, List.class);
        if (currentSemesterCourses == null) currentSemesterCourses = new ArrayList<>();

        // 2. 获取该学生所有的选课记录（含历史和当前）
        OperationDTO historyQuery = new OperationDTO();
        historyQuery.setOperation("SELECT");
        historyQuery.setTable("student_courses");
        historyQuery.setConditions(Map.of("student_id", studentId));
        List<Map<String, Object>> mySelections = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", historyQuery, List.class);
        if (mySelections == null) mySelections = new ArrayList<>();

        // 3. 分析选课历史
        Set<String> takenCourseNamesHistory = new HashSet<>(); // 历史学期已修过的课程名
        Set<String> currentSelectedIds = new HashSet<>();      // 当前学期已选的课程ID

        for (Map<String, Object> record : mySelections) {
            String recordSemester = (String) record.get("semester");
            String courseId = (String) record.get("course_id");

            if (currentSemester.equals(recordSemester)) {
                // 是本学期的选课
                currentSelectedIds.add(courseId);
            } else {
                // 是历史学期的选课，查询课程名以进行同名过滤
                String courseName = getCourseNameById(courseId);
                if (courseName != null) {
                    takenCourseNamesHistory.add(courseName);
                }
            }
        }

        // 4. 构建返回列表
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, String> teacherNameCache = new HashMap<>();

        for (Map<String, Object> course : currentSemesterCourses) {
            String courseName = (String) course.get("course_name");
            String courseId = (String) course.get("course_id");
            String teacherId = (String) course.get("teacher_id");

            // 【规则1】如果历史学期已经修过该同名课程，则直接在列表中过滤掉
            if (takenCourseNamesHistory.contains(courseName)) {
                continue;
            }

            // 组装数据
            Map<String, Object> vo = new HashMap<>(course);
            vo.put("is_selected", currentSelectedIds.contains(courseId));

            // 填充教师名
            if (!teacherNameCache.containsKey(teacherId)) {
                teacherNameCache.put(teacherId, getTeacherNameById(teacherId));
            }
            vo.put("teacher_name", teacherNameCache.get(teacherId));

            resultList.add(vo);
        }

        return resultList;
    }

    /**
     * 学生选课操作
     */
    public void selectCourse(String studentId, String courseId, String semester, String clientIp) {
        // 1. 检查是否重复选课
        OperationDTO checkDto = new OperationDTO();
        checkDto.setOperation("SELECT");
        checkDto.setTable("student_courses");
        checkDto.setConditions(Map.of("student_id", studentId, "course_id", courseId));
        Map<String, Object> exist = (Map<String, Object>) restTemplate.postForObject(
                serverUrl + "/select", checkDto, Map.class);

        if (exist != null && !exist.isEmpty()) {
            throw new RuntimeException("你已经选修了该课程");
        }

        // 2. 插入选课记录
        Map<String, Object> data = new HashMap<>();
        data.put("student_id", studentId);
        data.put("course_id", courseId);
        data.put("semester", semester);

        OperationDTO insertDto = new OperationDTO();
        insertDto.setOperation("INSERT");
        insertDto.setTable("student_courses");
        insertDto.setData(data);

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/insert", insertDto, Boolean.class);

        if (Boolean.TRUE.equals(success)) {
            sendAuditLog("STUDENT_SELECT_COURSE", "student_courses", studentId + "_" + courseId, studentId, "STUDENT", clientIp);
        } else {
            throw new RuntimeException("选课失败，请重试");
        }
    }

    /**
     * 学生退课
     */
    @SuppressWarnings("unchecked")
    public void dropCourse(String studentId, String courseId, String semester, String clientIp) {
        // 1. 确认确实选了这门课
        OperationDTO checkDto = new OperationDTO();
        checkDto.setOperation("SELECT");
        checkDto.setTable("student_courses");
        checkDto.setConditions(Map.of("student_id", studentId, "course_id", courseId));

        // 注意：这里使用 selectList 以兼容某些服务端实现，或者直接用select也可以，只要判断非空
        Map<String, Object> exist = (Map<String, Object>) restTemplate.postForObject(
                serverUrl + "/select", checkDto, Map.class);

        if (exist == null || exist.isEmpty()) {
            throw new RuntimeException("未找到该选课记录，无法退课");
        }

        // 2. 执行删除
        OperationDTO deleteDto = new OperationDTO();
        deleteDto.setOperation("DELETE");
        deleteDto.setTable("student_courses");
        deleteDto.setConditions(Map.of("student_id", studentId, "course_id", courseId));

        Boolean success = restTemplate.postForObject(serverUrl + "/manipulate/delete", deleteDto, Boolean.class);

        if (Boolean.TRUE.equals(success)) {
            sendAuditLog("STUDENT_DROP_COURSE", "student_courses", studentId + "_" + courseId, studentId, "STUDENT", clientIp);
        } else {
            throw new RuntimeException("退课失败");
        }
    }

    // --- 辅助方法 (如果类中已有 getStudentName等，可复用或重载，但这里需要针对ID查Name) ---

    @SuppressWarnings("unchecked")
    private String getCourseNameById(String courseId) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("courses");
        dto.setConditions(Map.of("course_id", courseId));
        Map<String, Object> c = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", dto, Map.class);
        return c != null ? (String) c.get("course_name") : null;
    }

    @SuppressWarnings("unchecked")
    private String getTeacherNameById(String teacherId) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("teachers");
        dto.setConditions(Map.of("teacher_id", teacherId));
        Map<String, Object> t = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", dto, Map.class);
        return t != null ? (String) t.get("name") : "未知";
    }
    @SuppressWarnings("unchecked")
    private boolean checkStudentSelected(String studentId, String courseId, String semester) {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("student_courses");
        dto.setConditions(Map.of(
                "student_id", studentId,
                "course_id", courseId,
                "semester", semester));
        List<Map<String, Object>> list =
                (List<Map<String, Object>>) restTemplate.postForObject(
                        serverUrl + "/selectList", dto, List.class);
        return list != null && !list.isEmpty();
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> calculateMajorRank(String courseId, String semester, String targetMajor, String myStudentId) {
        // 1. 获取该课程该学期所有已归档的成绩记录
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("grade_records");
        dto.setConditions(Map.of(
                "course_id", courseId,
                "semester", semester,
                "status", "SUBMITTED"
        ));

        List<Map<String, Object>> allRecords = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", dto, List.class);

        if (allRecords == null || allRecords.isEmpty()) {
            return Map.of("rank", "-", "total", 0);
        }

        // 2. 批量查询涉及的学生信息以筛选专业
        // (为了性能，先提取所有涉及的 student_id)
        Set<String> distinctStudentIds = allRecords.stream()
                .map(r -> (String) r.get("student_id"))
                .collect(Collectors.toSet());

        // 查询这些学生的专业信息
        // 注意：如果人数过多，这里应该分批查或优化 SQL。此处演示逻辑为主。
        // 为了简化 RPC 调用，我们可以反向操作：先查同专业所有学生，再匹配成绩
        OperationDTO stuDto = new OperationDTO();
        stuDto.setOperation("SELECT");
        stuDto.setTable("students");
        stuDto.setConditions(Map.of("major", targetMajor));
        List<Map<String, Object>> majorStudents = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", stuDto, List.class);

        if (majorStudents == null) majorStudents = new ArrayList<>();

        // 生成 同专业学生ID 的白名单 Set
        Set<String> majorStudentIdSet = majorStudents.stream()
                .map(s -> (String) s.get("student_id"))
                .collect(Collectors.toSet());

        // 3. [核心修改] 按学生ID分组，取最高分
        Map<String, Double> studentBestScoreMap = new HashMap<>();

        for (Map<String, Object> rec : allRecords) {
            String sid = (String) rec.get("student_id");

            // 只处理同专业的学生
            if (majorStudentIdSet.contains(sid)) {
                String encrypted = (String) rec.get("total_score_encrypted");
                if (encrypted != null) {
                    try {
                        double s = Double.parseDouble(aesUtil.decrypt(encrypted));

                        // 如果该学生已有成绩，取较高的那个 (例如：补考 67 > 正考 55)
                        studentBestScoreMap.merge(sid, s, Math::max);

                    } catch (Exception e) { /* 忽略解析错误 */ }
                }
            }
        }

        // 4. 提取分数列表并排序
        List<Double> uniqueScores = new ArrayList<>(studentBestScoreMap.values());
        // 降序排列 (从高到低)
        uniqueScores.sort(Collections.reverseOrder());

        // 5. 获取当前学生在 Map 中的最高分
        if (!studentBestScoreMap.containsKey(myStudentId)) {
            return Map.of("rank", "-", "total", uniqueScores.size());
        }

        double myBestScore = studentBestScoreMap.get(myStudentId);

        // 6. 计算排名
        // indexOf 返回第一个匹配项的下标 (0开始)，排名需 +1
        int rank = uniqueScores.indexOf(myBestScore) + 1;

        return Map.of("rank", rank, "total", uniqueScores.size());
    }
    /**
     * [新增辅助方法] 填充课程和教师信息（将原有逻辑提取出来，代码更整洁）
     */
    @SuppressWarnings("unchecked")
    private void fillCourseAndTeacherInfo(Map<String, Object> item, String courseId, String teacherId) {
        // 获取课程
        OperationDTO courseQuery = new OperationDTO();
        courseQuery.setTable("courses");
        courseQuery.setConditions(Map.of("course_id", courseId));
        Map<String, Object> course = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", courseQuery, Map.class);
        if (course != null) {
            item.put("course_name", course.get("course_name"));
            item.put("credit", course.get("credit"));
        }

        // 获取教师
        OperationDTO teacherQuery = new OperationDTO();
        teacherQuery.setTable("teachers");
        teacherQuery.setConditions(Map.of("teacher_id", teacherId));
        Map<String, Object> teacher = (Map<String, Object>) restTemplate.postForObject(serverUrl + "/select", teacherQuery, Map.class);
        item.put("teacher_name", teacher != null ? teacher.get("name") : teacherId);
    }
    /**
     * 学生查询自己的成绩 (含专业排名计算)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStudentGrades(String studentId, String semester, String clientIp) {
        // 1. 获取学生基本信息（为了拿到专业 major）
        OperationDTO stuDto = new OperationDTO();
        stuDto.setOperation("SELECT");
        stuDto.setTable("students");
        stuDto.setConditions(Map.of("student_id", studentId));
        Map<String, Object> studentInfo = (Map<String, Object>) restTemplate.postForObject(
                serverUrl + "/select", stuDto, Map.class);

        String myMajor = (String) studentInfo.get("major"); // 获取当前学生专业

        // 2. 查询成绩记录
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("student_id", studentId);
        conditions.put("status", "SUBMITTED");
        if (semester != null && !semester.isEmpty()) {
            conditions.put("semester", semester);
        }

        OperationDTO queryDTO = new OperationDTO();
        queryDTO.setOperation("SELECT");
        queryDTO.setTable("grade_records");
        queryDTO.setConditions(conditions);

        List<Map<String, Object>> gradeList = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", queryDTO, List.class);

        if (gradeList == null) gradeList = new ArrayList<>();

        List<Map<String, Object>> resultList = new ArrayList<>();

        // 3. 遍历每一门课，处理详情并计算排名
        for (Map<String, Object> record : gradeList) {
            Map<String, Object> item = new HashMap<>(record);

            // 解密各项成绩
            decryptAndPut(item, "total_score", "total_score_encrypted");
            decryptAndPut(item, "daily_score", "daily_score_encrypted");
            decryptAndPut(item, "final_score", "final_score_encrypted");
            decryptAndPut(item, "makeup_score", "makeup_score_encrypted");
            decryptAndPut(item, "attendance_score", "attendance_score_encrypted");
            decryptAndPut(item, "homework_score", "homework_score_encrypted");
            decryptAndPut(item, "experiment_score", "experiment_score_encrypted");
            decryptAndPut(item, "midterm_score", "midterm_score_encrypted");

            // 补充课程信息
            String courseId = (String) item.get("course_id");
            String recordSemester = (String) item.get("semester");

            // ... (原有的获取课程名、教师名逻辑保持不变) ...
            fillCourseAndTeacherInfo(item, courseId, (String) item.get("teacher_id"));

            // ================== [新增] 计算同专业排名逻辑 ==================
            if (myMajor != null) {
                try {
                    // 传入 myStudentId (即方法的参数 studentId)
                    Map<String, Object> rankInfo = calculateMajorRank(courseId, recordSemester, myMajor, studentId);
                    item.put("major_rank", rankInfo.get("rank"));
                    item.put("major_total_count", rankInfo.get("total"));
                } catch (Exception e) {
                    log.error("排名计算失败", e);
                    item.put("major_rank", "-");
                    item.put("major_total_count", "-");
                }
            } else {
                item.put("major_rank", "-");
                item.put("major_total_count", "-");
            }
            // ============================================================

            resultList.add(item);
        }

        sendAuditLog("STUDENT_QUERY_GRADE", "grade_records", studentId, studentId, "STUDENT", clientIp);
        return resultList;
    }
    /**
     * 完整成绩单：基本信息 + 成绩列表
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> getFullReport(String studentId, String semester, String clientIp) {
        // 1. 学生基本信息
        OperationDTO stuDTO = new OperationDTO();
        stuDTO.setOperation("SELECT");
        stuDTO.setTable("students");
        stuDTO.setConditions(Map.of("student_id", studentId));
        Map<String,Object> stu = (Map<String,Object>) restTemplate.postForObject(
                serverUrl + "/select", stuDTO, Map.class);
        if (stu == null || stu.isEmpty()) throw new RuntimeException("学生不存在");

        // 2. 成绩列表（复用原有逻辑，含解密、排名、教师名等）
        List<Map<String,Object>> grades = getStudentGrades(studentId, semester, clientIp);

        // 3. 封装返回
        Map<String,Object> report = new HashMap<>();
        report.put("student", Map.of(
                "studentId", stu.get("student_id"),
                "name", stu.get("name"),
                "className", stu.get("class_name"),
                "major", stu.get("major"),
                "email", stu.get("email")
        ));
        report.put("grades", grades);
        return report;
    }

    /**
     * 获取系统中的所有学期（去重、倒序）
     */
    @SuppressWarnings("unchecked")
    public List<String> getAllSemesters() {
        OperationDTO dto = new OperationDTO();
        dto.setOperation("SELECT");
        dto.setTable("courses");
        // 不加条件，查询所有课程以提取学期
        dto.setConditions(new HashMap<>());

        List<Map<String, Object>> list = (List<Map<String, Object>>) restTemplate.postForObject(
                serverUrl + "/selectList", dto, List.class);

        if (list == null) return new ArrayList<>();

        return list.stream()
                .map(c -> (String) c.get("semester"))
                .filter(Objects::nonNull)
                .distinct() // 去重
                .sorted(Comparator.reverseOrder()) // 倒序（最新的在前）
                .collect(Collectors.toList());
    }

}