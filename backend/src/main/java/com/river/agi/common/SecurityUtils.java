package com.river.agi.common;

import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class SecurityUtils {
    
    private final UserMapper userMapper;

    @Value("${encryption.master-key:river-agi-encryption-key-2026}")
    private String masterKey;

    @Value("${encryption.algorithm:AES/GCM/NoPadding}")
    private String algorithm;

    @Value("${encryption.enabled:false}")
    private boolean encryptionEnabled;

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public SecurityUtils(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 获取当前用户ID
     */
    public Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username)
            );
            if (user != null) {
                return user.getId();
            }
        }
        
        throw new IllegalStateException("Cannot determine user ID from authentication");
    }
    
    /**
     * 获取当前用户名
     */
    public String getCurrentUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        
        throw new IllegalStateException("Cannot determine username from authentication");
    }

    /**
     * 检查加密是否启用
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * 加密字符串
     */
    public String encrypt(String plainText) {
        if (!encryptionEnabled || plainText == null) {
            return plainText;
        }

        try {
            byte[] key = deriveKey(masterKey);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(algorithm);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密字符串
     */
    public String decrypt(String encryptedText) {
        if (!encryptionEnabled || encryptedText == null) {
            return encryptedText;
        }

        try {
            byte[] key = deriveKey(masterKey);
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(algorithm);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 派生密钥
     */
    private byte[] deriveKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                "river-agi-salt".getBytes(StandardCharsets.UTF_8),
                ITERATION_COUNT,
                KEY_LENGTH
        );
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * SHA-256 哈希
     */
    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("SHA-256 hashing failed", e);
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /**
     * 手机号脱敏
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return parts[0].charAt(0) + "***@" + parts[1];
        }
        return parts[0].substring(0, 3) + "***@" + parts[1];
    }

    /**
     * 身份证号脱敏
     */
    public String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 4) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡号脱敏
     */
    public String maskBankCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return cardNumber;
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * 姓名脱敏
     */
    public String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        return name.charAt(0) + repeat("*", name.length() - 1);
    }

    /**
     * 通用脱敏
     */
    public String mask(String data, String type) {
        if (data == null) {
            return null;
        }

        if (type == null) {
            return maskGeneric(data);
        }

        return switch (type.toUpperCase()) {
            case "PHONE", "MOBILE" -> maskPhone(data);
            case "EMAIL" -> maskEmail(data);
            case "ID_CARD", "ID_NUMBER" -> maskIdCard(data);
            case "BANK_CARD", "CARD_NO" -> maskBankCard(data);
            case "NAME", "REAL_NAME" -> maskName(data);
            default -> maskGeneric(data);
        };
    }

    /**
     * 通用脱敏 - 保留首尾各1个字符
     */
    private String maskGeneric(String data) {
        if (data.length() <= 2) {
            return data.charAt(0) + "*";
        }
        return data.charAt(0) + repeat("*", data.length() - 2) + data.charAt(data.length() - 1);
    }

    /**
     * 重复字符
     */
    private String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * 安全比较（防止时序攻击）
     */
    public boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成安全随机Token
     */
    public String generateSecureToken(int length) {
        byte[] tokenBytes = new byte[length];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
