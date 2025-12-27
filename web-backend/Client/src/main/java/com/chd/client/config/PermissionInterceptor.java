package com.chd.client.config;

import com.chd.client.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    @Autowired
    private com.chd.client.config.JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequirePermission permission = handlerMethod.getMethodAnnotation(RequirePermission.class);

        if (permission == null) {
            log.debug("无需权限验证: {}", request.getRequestURI());
            return true; // 无需权限验证
        }

        String token = request.getHeader("Authorization");
        log.debug("权限验证开始 - URI: {}, Token存在: {}", request.getRequestURI(), token != null);
        
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("缺少认证令牌 - URI: {}", request.getRequestURI());
            sendError(response, "缺少认证令牌");
            return false;
        }

        token = token.substring(7);
        log.debug("解析Token长度: {}", token.length());

        try {
            // ✅ 解析一次，复用结果
            var claims = jwtTokenUtil.parseToken(token);
            String role = (String) claims.get("role");
            String userId = (String) claims.get("userId");
            
            log.debug("Token解析成功 - userId: {}, role: {}", userId, role);

            // 检查角色权限
            String[] allowedRoles = permission.roles();
            boolean hasPermission = false;
            for (String allowedRole : allowedRoles) {
                if (allowedRole.equals(role)) {
                    hasPermission = true;
                    break;
                }
            }

            if (!hasPermission) {
                log.warn("权限不足 - userId: {}, role: {}, 需要角色: {}", userId, role, String.join(",", allowedRoles));
                sendError(response, "权限不足，需要角色: " + String.join(",", allowedRoles));
                return false;
            }

            // ✅ 设置请求属性（关键）
            request.setAttribute("userId", userId);
            request.setAttribute("userRole", role);
            request.setAttribute("userName", claims.get("name"));

            log.debug("权限验证通过 - userId: {}, role: {}, uri: {}", userId, role, request.getRequestURI());
            return true;
        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage(), e);
            sendError(response, "无效的认证令牌: " + e.getMessage());
            return false;
        }
    }

    private void sendError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.error(message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}