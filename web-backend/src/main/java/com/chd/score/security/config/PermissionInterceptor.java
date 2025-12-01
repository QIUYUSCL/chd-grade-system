package com.chd.score.security.config;

import com.chd.score.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

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

        // 从请求头获取 Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendError(response, "缺少认证令牌");
            return false;
        }

        token = token.substring(7);

        // 验证 Token
        if (!jwtTokenUtil.validateToken(token)) {
            sendError(response, "无效的认证令牌");
            return false;
        }

        // 获取用户角色
        String role = jwtTokenUtil.getRoleFromToken(token);

        // 检查权限
        String[] allowedRoles = permission.roles();
        boolean hasPermission = false;
        for (String allowedRole : allowedRoles) {
            if (allowedRole.equals(role)) {
                hasPermission = true;
                break;
            }
        }

        if (!hasPermission) {
            sendError(response, "权限不足");
            return false;
        }

        // 将用户信息存入请求属性
        request.setAttribute("userId", jwtTokenUtil.getUserIdFromToken(token));
        request.setAttribute("userRole", role);
        return true;
    }

    private void sendError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.error(message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}