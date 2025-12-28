package com.chd.client.config;

import com.chd.client.config.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtTokenUtil.parseToken(token);
                String userId = claims.get("userId", String.class);

                // 【修复点1】确认这里的 Key 是 "role" 还是 "userRole"？
                // 根据你之前的 JwtTokenUtil，这里大概率应该是 "role"
                String role = claims.get("role", String.class);

                // 如果上面获取不到，尝试用 "userRole" (双重保险)
                if (role == null) {
                    role = claims.get("userRole", String.class);
                }

                if (userId != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 【修复点2】SimpleGrantedAuthority 不允许为 null，加判空保护
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // 【修复点3】打印错误日志，否则不知道为什么 403
                log.error("JWT Filter 认证失败: {}", e.getMessage());
                // 这里不要 throw，否则前端收到的可能不是 403 而是 500
            }
        }

        chain.doFilter(request, response);
    }
}