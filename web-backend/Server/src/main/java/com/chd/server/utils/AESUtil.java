package com.chd.server.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AESUtil {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_SIZE = 16;
    private static final Logger log = LoggerFactory.getLogger(AESUtil.class);

    // ✅ 1. 移除 static，改为实例字段
    private SecretKey secretKey;

    @Value("${security.aes.secret-key:}")
    private String secretKeyConfig;

    @PostConstruct
    public void init() {
        try {
            if (secretKeyConfig == null || secretKeyConfig.trim().isEmpty()) {
                throw new IllegalArgumentException("AES密钥未配置！");
            }

            // 先Base64解码，再生成密钥
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyConfig.trim());

            // 验证密钥长度合法性
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "AES密钥长度非法: " + keyBytes.length + "字节，必须是16/24/32字节（128/192/256位）"
                );
            }

            this.secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
            log.info("AES初始化成功，密钥长度: {}位", secretKey.getEncoded().length * 8);
        } catch (Exception e) {
            log.error("AES初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("系统安全配置错误", e);
        }
    }


    public String encrypt(String plainData) {
        if (plainData == null || plainData.trim().isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainData.getBytes("UTF-8"));

            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

            return ivBase64 + ":" + encryptedBase64;
        } catch (Exception e) {
            log.error("AES加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据加密失败", e);
        }
    }


    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = encryptedData.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("密文格式错误");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            log.error("AES解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据解密失败", e);
        }
    }


    public void decryptRecords(List<Map<String, Object>> records, String encryptedField) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : records) {
            String encryptedValue = (String) record.get(encryptedField);
            if (encryptedValue != null) {
                try {
                    String decryptedValue = decrypt(encryptedValue);
                    record.put(encryptedField.replace("_encrypted", ""), decryptedValue);
                } catch (Exception e) {
                    log.warn("记录解密失败 ({}: {}), 跳过",
                            record.get("record_id"), encryptedField);
                    record.put(encryptedField.replace("_encrypted", ""), "**解密失败**");
                }
            }
        }
    }
}