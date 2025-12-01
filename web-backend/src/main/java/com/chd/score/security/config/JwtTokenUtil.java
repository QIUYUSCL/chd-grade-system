package com.chd.score.security.config;

import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class JwtTokenUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${security.jwt.secret}")
    private String secret;

    // 从 Token 中获取 Claims
    public Claims parseToken(String token) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            SecretKey key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());

            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Token 解析失败: {}", e.getMessage());
            throw new RuntimeException("无效的 Token");
        }
    }

    // 从 Token 中获取用户ID
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return (String) claims.get("userId");
    }

    // 从 Token 中获取用户角色
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return (String) claims.get("role");
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