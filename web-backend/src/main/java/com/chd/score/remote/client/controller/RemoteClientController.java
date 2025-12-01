package com.chd.score.remote.client.controller;

import com.chd.score.common.Result;
import com.chd.score.remote.client.dto.OperationDTO;
import com.chd.score.remote.client.service.RemoteClientService;
import com.chd.score.security.config.RequirePermission;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * 登录接口（无需权限验证）
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody OperationDTO dto, HttpServletRequest request) {
        log.info("收到登录请求: {}", dto);
        String clientIp = getClientIp(request);
        String token = clientService.executeLogin(dto, clientIp);
        log.info("登录成功, Token长度: {}", token.length());
        return Result.success(token);
    }

    /**
     * 普通用户修改自己的密码（需登录验证）
     * @param params 包含 oldPassword 和 newPassword
     * @param request 自动获取当前登录用户信息
     */
    @PostMapping("/password/change")
    @RequirePermission(roles = {"STUDENT", "TEACHER", "ADMIN"})
    public Result<String> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        // 从Token中获取当前用户信息（拦截器已验证并设置）
        String userId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");
        String clientIp = getClientIp(request);

        log.info("用户 {} 请求修改密码, IP: {}", userId, clientIp);

        try {
            boolean success = clientService.changePassword(oldPassword, newPassword, userId, role, clientIp);
            return success ? Result.success("密码修改成功，请重新登录") : Result.error("密码修改失败");
        } catch (RuntimeException e) {
            log.error("密码修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 管理员重置他人密码（无需旧密码，直接重置）
     * @param params 包含 targetUserId, targetRole, newPassword
     * @param request 自动获取当前管理员信息
     */
    @PostMapping("/password/admin/reset")
    @RequirePermission(roles = {"ADMIN"})
    public Result<String> adminResetPassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String targetUserId = params.get("targetUserId");
        String targetRole = params.get("targetRole");
        String newPassword = params.get("newPassword");

        String adminId = (String) request.getAttribute("userId");
        String adminIp = getClientIp(request);

        log.warn("管理员 {} 正在重置用户 {} ({}) 的密码，IP: {}", adminId, targetUserId, targetRole, adminIp);

        try {
            // 权限二次校验（防止水平越权）
            if (!"ADMIN".equals(request.getAttribute("userRole"))) {
                return Result.error("仅管理员可操作");
            }

            boolean success = clientService.adminResetPassword(targetUserId, targetRole, newPassword, adminId, adminIp);

            if (success) {
                log.warn("安全审计：管理员 {} 成功重置用户 {} 密码", adminId, targetUserId);
                return Result.success("密码重置成功，已记录审计日志");
            } else {
                return Result.error("密码重置失败");
            }
        } catch (RuntimeException e) {
            log.error("管理员重置密码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 教师查看成绩列表（分页）
     * @param semester 学期筛选
     * @param courseId 课程筛选
     * @param page 页码，默认1
     * @param pageSize 每页条数，默认10
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
        } catch (RuntimeException e) {
            log.error("成绩查看失败 - 教师: {}, 错误: {}", teacherId, e.getMessage());
            return Result.error("成绩查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/grade/entry")
    @RequirePermission(roles = {"TEACHER"})
    public Result<String> entryGrade(@RequestBody Map<String, Object> gradeData,
                                     HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        try {
            // ✅ 明确传递状态
            String status = (String) gradeData.get("status");
            log.info("教师 {} 录入成绩 - 状态: {}", teacherId, status);

            clientService.entryGrade(gradeData, teacherId, clientIp);
            return Result.success("成绩录入成功");
        } catch (Exception e) {
            log.error("成绩录入失败 - 用户: {}, IP: {}, 异常: ", teacherId, clientIp, e);
            return Result.error("成绩录入失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }


    /**
     * 教师获取自己的课程列表（动态加载）
     */
    @GetMapping("/teacher/courses")
    @RequirePermission(roles = {"TEACHER"})
    public Result<List<Map<String, Object>>> getTeacherCourses(HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("userId");
        String clientIp = getClientIp(request);

        log.info("教师 {} 请求课程列表, IP: {}", teacherId, clientIp);

        try {
            List<Map<String, Object>> courses = clientService.getTeacherCourses(teacherId);
            return Result.success(courses);
        } catch (Exception e) {
            log.error("获取课程列表失败 - 教师: {}, 错误: {}", teacherId, e.getMessage());
            return Result.error("获取课程列表失败: " + e.getMessage());
        }
    }



}