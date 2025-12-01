package com.chd.score.security.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AES加密解密工具类
 * 用于敏感数据（成绩、个人信息）的加密存储和解密展示
 * 密钥从配置文件中读取，禁止硬编码
 */
@Component
public class AESUtil {

    // 加密算法/模式/填充方式
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    // 密钥长度（128/192/256位）
    private static final int KEY_SIZE = 256;

    // IV向量长度（16字节）
    private static final int IV_SIZE = 16;

    // 从配置文件中读取密钥（需16/24/32字节）
    @Value("${security.aes.secret-key:}")
    private String secretKeyConfig;

    // 静态密钥实例，供静态方法使用
    private static SecretKey secretKey;
    private static final Object lock = new Object();

    @PostConstruct
    public void init() {
        synchronized (lock) {
            if (secretKey == null) {
                try {
                    // 1. 校验密钥配置
                    if (secretKeyConfig == null || secretKeyConfig.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "AES密钥未配置！请在application.yml中设置security.aes.secret-key"
                        );
                    }

                    // 2. 生成密钥（支持32字节密钥）
                    byte[] keyBytes = secretKeyConfig.getBytes("UTF-8");
                    secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);

                    log.info("AES加密组件初始化成功，密钥长度: {}位", secretKey.getEncoded().length * 8);

                } catch (Exception e) {
                    log.error("AES加密组件初始化失败: {}", e.getMessage(), e);
                    throw new RuntimeException("系统安全配置错误", e);
                }
            }
        }
    }

    /**
     * 加密数据（存储到数据库前调用）
     * @param plainData 明文数据
     * @return Base64编码的密文（格式：base64(IV):base64(密文)）
     */
    public static String encrypt(String plainData) {
        if (plainData == null || plainData.trim().isEmpty()) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            // 生成随机IV向量（每次加密不同，增强安全性）
            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 初始化加密器
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            // 执行加密
            byte[] encryptedBytes = cipher.doFinal(plainData.getBytes("UTF-8"));

            // 组合IV和密文（格式：base64(IV):base64(密文)）
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

            return ivBase64 + ":" + encryptedBase64;

        } catch (Exception e) {
            log.error("AES加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * 解密数据（从数据库读取后调用）
     * @param encryptedData 密文数据（格式：base64(IV):base64(密文)）
     * @return 明文
     */
    public static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return null;
        }

        try {
            // 解析IV和密文
            String[] parts = encryptedData.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("密文格式错误，缺少IV或数据");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 初始化解密器
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            // 执行解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, "UTF-8");

        } catch (Exception e) {
            log.error("AES解密失败: 密文长度={}, 错误={}", encryptedData.length(), e.getMessage());
            throw new RuntimeException("数据解密失败", e);
        }
    }

    /**
     * 批量解密成绩列表
     * @param records 数据库查询结果
     * @param encryptedField 需要解密的字段名
     */
    public static void decryptRecords(List<Map<String, Object>> records, String encryptedField) {
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AESUtil.class);
}