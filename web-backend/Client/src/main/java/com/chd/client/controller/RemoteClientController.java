package com.chd.client.controller;

import com.chd.client.service.RemoteClientService;
import com.chd.client.dto.OperationDTO;
import com.chd.client.utils.Result; // 假设Result类已复制到client模块
import com.chd.client.config.RequirePermission; // 假设注解已复制
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/remote/client")
public class RemoteClientController {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientController.class);

    private final RemoteClientService clientService;
    @Autowired
    private RestTemplate restTemplate;

    @Value("${remote.server.url}")
    private String serverUrl;

    public RemoteClientController(RemoteClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody OperationDTO dto, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        try {
            String token = clientService.executeLogin(dto, clientIp);
            return Result.success(token);
        } catch (RuntimeException e) {
            log.error("登录失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 简单的连接测试端点
     */
    @GetMapping("/test")
    public Result<String> testConnection() {
        return Result.success("连接正常，服务运行中");
    }

    /**
     * 管理员权限测试端点
     */
    @GetMapping("/admin/test")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminTest(HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");
        return Result.success("管理员权限验证通过 - ID: " + adminId + ", Role: " + role);
    }

    /**
     * 修改密码
     */
    @PostMapping("/password/change")
    @RequirePermission(roles = {"STUDENT", "TEACHER", "ADMIN"})
    public Result<String> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        String userId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");
        String clientIp = getClientIp(request);

        try {
            boolean success = clientService.changePassword(oldPassword, newPassword, userId, role, clientIp);
            return success ? Result.success("密码修改成功") : Result.error("密码修改失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 管理员重置密码
     */
    @PostMapping("/password/admin/reset")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminResetPassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String targetUserId = params.get("targetUserId");
        String targetRole = params.get("targetRole");
        String newPassword = params.get("newPassword");
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        try {
            boolean success = clientService.adminResetPassword(targetUserId, targetRole, newPassword, adminId, clientIp);
            return success ? Result.success("重置成功") : Result.error("重置失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }


    @PostMapping("/teacher/info")
    public Result<Map<String, Object>> getTeacherInfo(@RequestBody Map<String, String> params) {
        String teacherId = params.get("teacherId");
        if (teacherId == null || teacherId.isEmpty()) {
            return Result.error("教师ID不能为空");
        }

        try {
            Map<String, Object> info = clientService.getTeacherInfo(teacherId);
            if (info == null || info.isEmpty()) {
                return Result.error("未找到教师信息");
            }
            return Result.success(info);
        } catch (Exception e) {
            log.error("获取教师信息失败", e);
            return Result.error("系统错误: " + e.getMessage());
        }
    }


    /**
     * 教师查看成绩
     */
    @GetMapping("/grade/view")
    @RequirePermission(roles = {"TEACHER"})
    public Result<Map<String, Object>> viewGrades(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String className, // ✅ [新增] 接收班级参数
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {

        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        try {
            // ✅ [修改] 将 className 传给 Service
            Map<String, Object> result = clientService.viewGrades(
                    teacherId, semester, courseId, examType, status, className, page, pageSize, clientIp
            );
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }


    /**
     * 教师录入成绩 (支持多项成绩)
     */
    @PostMapping("/grade/entry")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> entryGrade(@RequestBody Map<String, Object> gradeData, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        try {
            clientService.entryGrade(gradeData, teacherId, clientIp);
            return Result.success("成绩录入成功");
        } catch (Exception e) {
            return Result.error("录入失败: " + e.getMessage());
        }
    }

    /**
     * 教师修改成绩 (支持多项成绩)
     */
    @PostMapping("/grade/update")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> updateGrade(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        // 获取记录ID
        String recordId = String.valueOf(params.get("recordId"));

        // 获取包含所有明文成绩的Map (daily_score, final_score, attendance_score ...)
        Map<String, Object> plainData = (Map<String, Object>) params.get("data");

        try {
            // 直接传递 Map 给 Service 处理
            boolean success = clientService.updateGradeWithEncryption(recordId, plainData, teacherId, clientIp);
            return success ? Result.success("修改成功") : Result.error("修改失败");
        } catch (Exception e) {
            return Result.error("修改失败: " + e.getMessage());
        }
    }

    /**
     * 教师获取课程列表
     */
    @GetMapping("/teacher/courses")
    @RequirePermission(roles = {"TEACHER"})
    public Result<List<Map<String, Object>>> getTeacherCourses(HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        try {
            return Result.success(clientService.getTeacherCourses(teacherId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) ? request.getRemoteAddr() : ip;
    }

    /**
     * 教师撤销成绩
     */
    @PostMapping("/grade/revoke")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> revokeGrade(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        String recordId = params.get("recordId");

        try {
            clientService.revokeGrade(recordId, teacherId, clientIp);
            return Result.success("成绩撤销成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取学生姓名 (用于单条录入回显)
     */
    @GetMapping("/student/name")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> getStudentName(@RequestParam String studentId) {
        try {
            return Result.success(clientService.getStudentName(studentId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据课程获取选课学生列表 (用于批量录入)
     */
    @GetMapping("/course/students")
    @RequirePermission(roles = {"TEACHER"})
    public Result<List<Map<String, Object>>> getStudentsByCourse(
            @RequestParam String courseId,
            @RequestParam String semester) {
        try {
            // 调用 Service 层的新方法
            return Result.success(clientService.getStudentsByCourse(courseId, semester));
        } catch (Exception e) {
            return Result.error("获取选课学生失败: " + e.getMessage());
        }
    }

    /**
     * 批量录入成绩
     */
    @PostMapping("/grade/batch-entry")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> batchEntryGrade(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        List<Map<String, Object>> grades = (List<Map<String, Object>>) params.get("grades");

        try {
            clientService.batchEntryGrade(grades, teacherId, clientIp);
            return Result.success("批量录入成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取成绩统计数据
     */
    @GetMapping("/grade/stats")
    @RequirePermission(roles = {"TEACHER"})
    public Result<Map<String, Object>> getGradeStats(
            @RequestParam String courseId,
            @RequestParam String semester,
            @RequestParam(defaultValue = "正考") String examType) {
        try {
            return Result.success(clientService.calculateGradeStats(courseId, semester, examType));
        } catch (Exception e) {
            return Result.error("统计失败: " + e.getMessage());
        }
    }

    /**
     * 保存成绩分析报告
     */
    @PostMapping("/grade/analysis/save")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> saveAnalysis(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        try {
            clientService.saveAnalysis(params, teacherId, clientIp);
            return Result.success("分析报告保存成功");
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取已保存的分析报告
     */
    @GetMapping("/grade/analysis/get")
    @RequirePermission(roles = {"TEACHER"})
    public Result<Map<String, Object>> getAnalysis(
            @RequestParam String courseId,
            @RequestParam String semester,
            HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        try {
            return Result.success(clientService.getAnalysis(courseId, semester, teacherId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 管理员功能 ====================

    /**
     * 管理员查询成绩记录（支持多条件筛选）
     */
    @PostMapping("/admin/grade/query")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminQueryGrades(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            Map<String, Object> result = clientService.adminQueryGrades(params, adminId, clientIp);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 管理员小撤销：将已提交成绩改为草稿状态
     */
    @PostMapping("/admin/grade/minor-revoke")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminMinorRevoke(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        String recordId = String.valueOf(params.get("recordId"));
        
        try {
            clientService.adminMinorRevoke(recordId, adminId, clientIp);
            return Result.success("小撤销成功，成绩已转为草稿状态");
        } catch (Exception e) {
            return Result.error("小撤销失败: " + e.getMessage());
        }
    }

    /**
     * 管理员大撤销：完全删除成绩记录
     */
    @PostMapping("/admin/grade/major-revoke")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminMajorRevoke(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        String recordId = String.valueOf(params.get("recordId"));
        
        try {
            clientService.adminMajorRevoke(recordId, adminId, clientIp);
            return Result.success("大撤销成功，成绩记录已删除");
        } catch (Exception e) {
            return Result.error("大撤销失败: " + e.getMessage());
        }
    }

    /**
     * 管理员获取用户列表（学生/教师/管理员）
     */
    @PostMapping("/admin/users/query")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminQueryUsers(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            Map<String, Object> result = clientService.adminQueryUsers(params, adminId, clientIp);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 管理员添加用户
     */
    @PostMapping("/admin/users/add")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminAddUser(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminAddUser(params, adminId, clientIp);
            return Result.success("用户添加成功");
        } catch (Exception e) {
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 管理员更新用户信息
     */
    @PostMapping("/admin/users/update")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminUpdateUser(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminUpdateUser(params, adminId, clientIp);
            return Result.success("用户信息更新成功");
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 管理员删除用户
     */
    @PostMapping("/admin/users/delete")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminDeleteUser(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminDeleteUser(params, adminId, clientIp);
            return Result.success("用户删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 管理员查询安全日志
     */
    @PostMapping("/admin/security-log/query")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminQuerySecurityLog(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            Map<String, Object> result = clientService.adminQuerySecurityLog(params, adminId, clientIp);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 管理员数据完整性校验
     */
    @PostMapping("/admin/data/verify")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminVerifyDataIntegrity(HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            Map<String, Object> result = clientService.adminVerifyDataIntegrity(adminId, clientIp);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("校验失败: " + e.getMessage());
        }
    }

    // ==================== 课程管理功能 ====================

    /**
     * 管理员查询课程列表
     */
    @PostMapping("/admin/courses/query")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminQueryCourses(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        log.info("管理员课程查询请求 - adminId: {}, clientIp: {}, params: {}", adminId, clientIp, params);
        
        try {
            Map<String, Object> result = clientService.adminQueryCourses(params, adminId, clientIp);
            log.info("管理员课程查询成功 - adminId: {}, 结果数量: {}", adminId, 
                    result.get("list") != null ? ((List<?>) result.get("list")).size() : 0);
            return Result.success(result);
        } catch (Exception e) {
            log.error("管理员课程查询失败 - adminId: {}, 错误: {}", adminId, e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 管理员添加课程
     */
    @PostMapping("/admin/courses/add")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminAddCourse(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminAddCourse(params, adminId, clientIp);
            return Result.success("课程添加成功");
        } catch (Exception e) {
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 管理员更新课程信息
     */
    @PostMapping("/admin/courses/update")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminUpdateCourse(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminUpdateCourse(params, adminId, clientIp);
            return Result.success("课程信息更新成功");
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 管理员删除课程
     */
    @PostMapping("/admin/courses/delete")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminDeleteCourse(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminDeleteCourse(params, adminId, clientIp);
            return Result.success("课程删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 管理员查询选课关系
     */
    @PostMapping("/admin/student-courses/query")
    @RequirePermission(roles = {"ADMIN"})
    public Result<Map<String, Object>> adminQueryStudentCourses(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            Map<String, Object> result = clientService.adminQueryStudentCourses(params, adminId, clientIp);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 管理员添加选课关系
     */
    @PostMapping("/admin/student-courses/add")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminAddStudentCourse(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminAddStudentCourse(params, adminId, clientIp);
            return Result.success("选课关系添加成功");
        } catch (Exception e) {
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 管理员删除选课关系
     */
    @PostMapping("/admin/student-courses/delete")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminDeleteStudentCourse(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String adminId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        
        try {
            clientService.adminDeleteStudentCourse(params, adminId, clientIp);
            return Result.success("选课关系删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师列表（用于课程管理下拉选择）
     */
    @GetMapping("/admin/teachers/list")
    @RequirePermission(roles = {"ADMIN"})
    public Result<List<Map<String, Object>>> getTeachersList(HttpServletRequest request) {
        try {
            List<Map<String, Object>> teachers = clientService.getTeachersList();
            return Result.success(teachers);
        } catch (Exception e) {
            return Result.error("获取教师列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取院系列表（用于课程管理下拉选择）
     */
    @GetMapping("/admin/departments/list")
    @RequirePermission(roles = {"ADMIN"})
    public Result<List<String>> getDepartmentsList(HttpServletRequest request) {
        try {
            List<String> departments = clientService.getDepartmentsList();
            return Result.success(departments);
        } catch (Exception e) {
            return Result.error("获取院系列表失败: " + e.getMessage());
        }
    }
    // ✅ 新增接口：学生成绩、选课、教师查询学生选课
// ✅ 已保留第一次所有接口，补充第二次新增部分

    // ✅ 新增：学生查询自己的成绩
    @GetMapping("/student/my-grades")
    @RequirePermission(roles = {"STUDENT"})
    public Result<List<Map<String, Object>>> getMyGrades(
            @RequestParam(required = false) String semester,
            HttpServletRequest request) {
        String studentId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        try {
            List<Map<String, Object>> grades = clientService.getStudentGrades(studentId, semester, clientIp);
            return Result.success(grades);
        } catch (Exception e) {
            log.error("学生查询成绩失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    // ✅ 新增：学生选课列表
    @GetMapping("/student/course/selection-list")
    @RequirePermission(roles = {"STUDENT"})
    public Result<List<Map<String, Object>>> getCourseSelectionList(
            @RequestParam String semester,
            HttpServletRequest request) {
        String studentId = (String) request.getAttribute("userId");
        try {
            return Result.success(clientService.getStudentCourseSelectionList(studentId, semester));
        } catch (Exception e) {
            return Result.error("加载失败: " + e.getMessage());
        }
    }

    // ✅ 新增：学生选课
    @PostMapping("/student/course/select")
    @RequirePermission(roles = {"STUDENT"})
    public Result<String> selectCourse(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String studentId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        String courseId = params.get("courseId");
        String semester = params.get("semester");
        try {
            clientService.selectCourse(studentId, courseId, semester, clientIp);
            return Result.success("选课成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ✅ 新增：学生退课
    @PostMapping("/student/course/drop")
    @RequirePermission(roles = {"STUDENT"})
    public Result<String> dropCourse(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String studentId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        String courseId = params.get("courseId");
        String semester = params.get("semester");
        try {
            clientService.dropCourse(studentId, courseId, semester, clientIp);
            return Result.success("退课成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ✅ 新增：教师查询学生本学期已选课程（用于成绩录入下拉）
    @GetMapping("/student/selected-courses")
    @RequirePermission(roles = {"TEACHER"})
    public Result<List<Map<String, Object>>> getStudentSelectedCourses(
            @RequestParam String studentId,
            @RequestParam String semester) {
        log.info("【选课查询】studentId={}, semester={}", studentId, semester);
        try {
            OperationDTO scDto = new OperationDTO();
            scDto.setOperation("SELECT");
            scDto.setTable("student_courses");
            scDto.setConditions(Map.of("student_id", studentId, "semester", semester));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scList = (List<Map<String, Object>>) restTemplate.postForObject(
                    serverUrl + "/selectList", scDto, List.class);
            if (scList == null || scList.isEmpty()) {
                return Result.success(List.of());
            }
            List<String> courseIds = scList.stream().map(r -> (String) r.get("course_id")).collect(Collectors.toList());
            OperationDTO cDto = new OperationDTO();
            cDto.setOperation("SELECT");
            cDto.setTable("courses");
            cDto.setConditions(Map.of("course_id_in", courseIds));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> courses = (List<Map<String, Object>>) restTemplate.postForObject(
                    serverUrl + "/selectList", cDto, List.class);
            List<Map<String, Object>> voList = new ArrayList<>();
            if (courses != null) {
                for (Map<String, Object> c : courses) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("course_id", c.get("course_id"));
                    item.put("course_name", c.get("course_name"));
                    item.put("credit", c.get("credit"));
                    voList.add(item);
                }
            }
            return Result.success(voList);
        } catch (Exception e) {
            log.error("【选课查询失败】", e);
            return Result.error("选课查询失败: " + e.getMessage());
        }
    }
    /**
     * 学生个人完整成绩单（含基本信息）
     */
    @GetMapping("/student/full-report")
    @RequirePermission(roles = {"STUDENT"})
    public Result<Map<String,Object>> getFullReport(
            @RequestParam(required = false) String semester,
            HttpServletRequest request) {
        String studentId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);
        try {
            Map<String,Object> resp = clientService.getFullReport(studentId, semester, clientIp);
            return Result.success(resp);
        } catch (Exception e) {
            log.error("完整成绩单失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有学期列表（通用接口）
     */
    @GetMapping("/common/semesters")
    @RequirePermission(roles = {"STUDENT", "TEACHER", "ADMIN"})
    public Result<List<String>> getSemesters() {
        try {
            return Result.success(clientService.getAllSemesters());
        } catch (Exception e) {
            return Result.error("获取学期失败: " + e.getMessage());
        }
    }



}