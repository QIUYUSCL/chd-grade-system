package com.chd.client.controller;

import com.chd.client.service.RemoteClientService;
import com.chd.client.dto.OperationDTO;
import com.chd.client.utils.Result; // 假设Result类已复制到client模块
import com.chd.client.config.RequirePermission; // 假设注解已复制
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/remote/client")
public class RemoteClientController {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientController.class);

    private final RemoteClientService clientService;

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

    /**
     * 教师查看成绩
     */
    @GetMapping("/grade/view")
    @RequirePermission(roles = {"TEACHER"})
    public Result<Map<String, Object>> viewGrades(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {

        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        try {
            Map<String, Object> result = clientService.viewGrades(teacherId, semester, courseId, page, pageSize, clientIp);
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
}