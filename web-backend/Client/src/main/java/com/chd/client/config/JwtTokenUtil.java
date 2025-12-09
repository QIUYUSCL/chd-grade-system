package com.chd.client.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
public class JwtTokenUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${security.jwt.secret}")
    private String secret;

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 从 Token 中获取 Claims
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey()) // 0.12.x 变更: verifyWith
                    .build()
                    .parseSignedClaims(token)   // 0.12.x 变更: parseSignedClaims
                    .getPayload();              // 0.12.x 变更: getPayload
        } catch (Exception e) {
            log.error("Token 解析失败: {}", e.getMessage());
            throw new RuntimeException("无效的 Token");
        }
    }

    // 从 Token 中获取用户ID
    public String getUserIdFromToken(String token) {
        return parseToken(token).get("userId", String.class);
    }

    // 从 Token 中获取用户角色
    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}