package com.chd.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF (对于非浏览器客户端的 RPC 调用必须关闭)
                .csrf(csrf -> csrf.disable())

                // 2. 授权规则：允许所有请求 (因为这是内部数据服务)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/rpc/**").permitAll() // 明确放行 RPC 接口
                        .anyRequest().permitAll()               // 建议开发阶段放行所有
                );

        return http.build();
    }
}