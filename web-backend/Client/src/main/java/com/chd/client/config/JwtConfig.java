package com.chd.client.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
public class JwtConfig {

    @Value("${jwt.secret:Y2hkLWdyYWRlLW1hbmFnZW1lbnQtc2VjcmV0LWtleS0yMDI1LTMyYnl0ZXM=}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private Long expiration;

    public String generateToken(Map<String, Object> claims) {
        // 解码 Base64 字符串为密钥
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes); // 创建安全的密钥

        return Jwts.builder()
                .claims(claims)  // 使用新版本API
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(key) // 使用密钥对象签名
                .compact();
    }
}