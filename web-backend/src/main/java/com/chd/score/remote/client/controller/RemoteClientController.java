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

import java.util.Map;

@RestController
@RequestMapping("/remote/client")
public class RemoteClientController {

    private static final Logger log = LoggerFactory.getLogger(RemoteClientController.class);

    private final RemoteClientService clientService;

    public RemoteClientController(RemoteClientService clientService) {
        this.clientService = clientService;
    }


    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody OperationDTO dto, HttpServletRequest request) {
        log.info("收到登录请求: {}", dto);  // 打印请求体
        log.info("请求IP: {}", getClientIp(request));

        String clientIp = getClientIp(request);
        String token = clientService.executeLogin(dto, clientIp);
        log.info("登录成功, Token长度: {}", token.length());

        return Result.success(token);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 修改密码接口（需要登录）
     */
    @PostMapping("/password/change")
    @RequirePermission(roles = {"STUDENT", "TEACHER", "ADMIN"})
    public Result<String> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        // 从请求属性获取用户信息（拦截器已验证并设置）
        String userId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("userRole");
        String clientIp = getClientIp(request); // 获取客户端IP

        log.info("用户 {} 请求修改密码, IP: {}", userId, clientIp);

        try {
            boolean success = clientService.changePassword(oldPassword, newPassword, userId, role, clientIp);
            if (success) {
                log.info("用户 {} 密码修改成功", userId);
                return Result.success("密码修改成功");
            } else {
                return Result.error("密码修改失败");
            }
        } catch (RuntimeException e) {
            log.error("密码修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

}
