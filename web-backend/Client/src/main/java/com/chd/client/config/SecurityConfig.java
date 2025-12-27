package com.chd.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. 关闭CSRF（开发阶段）
                .csrf(csrf -> csrf.disable())

                // 3. 配置授权规则（关键：明确放行登录接口）
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/remote/client/login").permitAll()  // 精确放行登录接口
                        .requestMatchers("/remote/client/**").permitAll()      // 临时：放行所有测试接口（仅开发阶段）
                        .anyRequest().denyAll()  // 其他请求拒绝（生产环境改为authenticated()）
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 添加域名和云服务器IP
        config.addAllowedOrigin("https://www.testscl.xyz");
        config.addAllowedOrigin("http://www.testscl.xyz");
        config.addAllowedOrigin("http://8.218.190.69");

        // 添加本地开发端口 - 扩展更多端口
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost:3001");
        config.addAllowedOrigin("http://localhost:4173");
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:5174");
        config.addAllowedOrigin("http://localhost:8080");
        config.addAllowedOrigin("http://localhost:8081");
        config.addAllowedOrigin("http://127.0.0.1:3000");
        config.addAllowedOrigin("http://127.0.0.1:3001");
        config.addAllowedOrigin("http://127.0.0.1:4173");
        config.addAllowedOrigin("http://127.0.0.1:5173");
        config.addAllowedOrigin("http://127.0.0.1:5174");
        config.addAllowedOrigin("http://127.0.0.1:8080");
        config.addAllowedOrigin("http://127.0.0.1:8081");
        config.addAllowedOrigin("http://10.81.138.82:5174");

        // 添加通配符支持（开发环境）
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

