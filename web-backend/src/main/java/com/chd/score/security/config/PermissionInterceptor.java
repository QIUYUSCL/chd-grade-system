package com.chd.score.security.config;

import com.chd.score.common.Result;
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
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequirePermission permission = handlerMethod.getMethodAnnotation(RequirePermission.class);

        if (permission == null) {
            return true; // 无需权限验证
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendError(response, "缺少认证令牌");
            return false;
        }

        token = token.substring(7);

        try {
            // ✅ 解析一次，复用结果
            var claims = jwtTokenUtil.parseToken(token);
            String role = (String) claims.get("role");
            String userId = (String) claims.get("userId");

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
            log.error("Token解析失败: {}", e.getMessage());
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