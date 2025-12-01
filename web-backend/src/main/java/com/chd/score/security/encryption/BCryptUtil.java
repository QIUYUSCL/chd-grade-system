package com.chd.score.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final Logger log = LoggerFactory.getLogger(BCryptUtil.class);

    public static String hash(String password) {
        return encoder.encode(password);
    }

    public static boolean verify(String rawPassword, String hashedPassword) {
        log.debug("验证密码 - 输入: {}, 哈希: {}", rawPassword, hashedPassword);

        if (rawPassword == null || hashedPassword == null) {
            log.warn("密码或哈希为空");
            return false;
        }

        boolean result = encoder.matches(rawPassword, hashedPassword);
        log.debug("验证结果: {}", result);
        return result;
    }


    public static void main(String[] args) {
        String password = "111111";
        String hash = hash(password);
        System.out.println("========================================");
        System.out.println("明文密码: " + password);
        System.out.println("BCrypt 哈希: " + hash);
        System.out.println("========================================");
    }
}