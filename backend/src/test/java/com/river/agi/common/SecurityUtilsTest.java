package com.river.agi.common;

import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("安全工具类测试")
class SecurityUtilsTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private Authentication authentication;
    @Mock
    private UserDetails userDetails;

    private SecurityUtils securityUtils;

    @BeforeEach
    void setUp() throws Exception {
        securityUtils = new SecurityUtils(userMapper);

        Field masterKeyField = SecurityUtils.class.getDeclaredField("masterKey");
        masterKeyField.setAccessible(true);
        masterKeyField.set(securityUtils, "test-master-key-2026");

        Field algorithmField = SecurityUtils.class.getDeclaredField("algorithm");
        algorithmField.setAccessible(true);
        algorithmField.set(securityUtils, "AES/GCM/NoPadding");

        Field encryptionEnabledField = SecurityUtils.class.getDeclaredField("encryptionEnabled");
        encryptionEnabledField.setAccessible(true);
        encryptionEnabledField.set(securityUtils, true);
    }

    // ===== getCurrentUserId / getCurrentUsername =====

    @Test
    @DisplayName("getCurrentUserId - 认证为 null 抛异常")
    void getCurrentUserId_nullAuth_throws() {
        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUserId(null));
    }

    @Test
    @DisplayName("getCurrentUserId - 未认证抛异常")
    void getCurrentUserId_notAuthenticated_throws() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUserId(authentication));
    }

    @Test
    @DisplayName("getCurrentUserId - principal 非 UserDetails 抛异常")
    void getCurrentUserId_notUserDetails_throws() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("string-principal");

        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUserId(authentication));
    }

    @Test
    @DisplayName("getCurrentUserId - 用户不存在抛异常")
    void getCurrentUserId_userNotFound_throws() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("unknown");
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUserId(authentication));
    }

    @Test
    @DisplayName("getCurrentUserId - 成功返回用户 ID")
    void getCurrentUserId_success() {
        User user = new User();
        user.setId(42L);
        user.setUsername("admin");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("admin");
        when(userMapper.selectOne(any())).thenReturn(user);

        Long userId = securityUtils.getCurrentUserId(authentication);

        assertEquals(42L, userId);
    }

    @Test
    @DisplayName("getCurrentUsername - 认证为 null 抛异常")
    void getCurrentUsername_nullAuth_throws() {
        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUsername(null));
    }

    @Test
    @DisplayName("getCurrentUsername - 成功返回用户名")
    void getCurrentUsername_success() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("admin");

        String username = securityUtils.getCurrentUsername(authentication);

        assertEquals("admin", username);
    }

    @Test
    @DisplayName("getCurrentUsername - 未认证抛异常")
    void getCurrentUsername_notAuthenticated_throws() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> securityUtils.getCurrentUsername(authentication));
    }

    // ===== encrypt / decrypt =====

    @Test
    @DisplayName("isEncryptionEnabled - 返回 true")
    void isEncryptionEnabled_true() {
        assertTrue(securityUtils.isEncryptionEnabled());
    }

    @Test
    @DisplayName("encrypt - 加密 enabled 时加密字符串")
    void encrypt_enabled() {
        String plainText = "hello world";
        String encrypted = securityUtils.encrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
    }

    @Test
    @DisplayName("encrypt - null 输入返回 null")
    void encrypt_null() {
        assertNull(securityUtils.encrypt(null));
    }

    @Test
    @DisplayName("decrypt - 解密加密后的字符串")
    void decrypt_success() {
        String plainText = "secret data";
        String encrypted = securityUtils.encrypt(plainText);
        String decrypted = securityUtils.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("decrypt - null 输入返回 null")
    void decrypt_null() {
        assertNull(securityUtils.decrypt(null));
    }

    @Test
    @DisplayName("encrypt/decrypt - 中文字符")
    void encryptDecrypt_chinese() {
        String plainText = "中文测试数据";
        String encrypted = securityUtils.encrypt(plainText);
        String decrypted = securityUtils.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("encrypt/decrypt - 空字符串")
    void encryptDecrypt_emptyString() {
        String encrypted = securityUtils.encrypt("");
        String decrypted = securityUtils.decrypt(encrypted);

        assertEquals("", decrypted);
    }

    // ===== sha256 =====

    @Test
    @DisplayName("sha256 - 返回 Base64 编码的哈希")
    void sha256_success() {
        String hash = securityUtils.sha256("test");

        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    @DisplayName("sha256 - 相同输入产生相同输出")
    void sha256_deterministic() {
        String hash1 = securityUtils.sha256("input");
        String hash2 = securityUtils.sha256("input");

        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("sha256 - 不同输入产生不同输出")
    void sha256_differentInputs() {
        String hash1 = securityUtils.sha256("input1");
        String hash2 = securityUtils.sha256("input2");

        assertNotEquals(hash1, hash2);
    }

    // ===== 脱敏方法 =====

    @Test
    @DisplayName("maskPhone - 标准手机号脱敏")
    void maskPhone_standard() {
        assertEquals("138****1234", securityUtils.maskPhone("13812341234"));
    }

    @Test
    @DisplayName("maskPhone - 短号码原样返回")
    void maskPhone_short() {
        assertEquals("12345", securityUtils.maskPhone("12345"));
    }

    @Test
    @DisplayName("maskPhone - null 返回 null")
    void maskPhone_null() {
        assertNull(securityUtils.maskPhone(null));
    }

    @Test
    @DisplayName("maskEmail - 标准邮箱脱敏")
    void maskEmail_standard() {
        assertEquals("abc***@example.com", securityUtils.maskEmail("abcdef@example.com"));
    }

    @Test
    @DisplayName("maskEmail - 短用户名邮箱脱敏")
    void maskEmail_shortUsername() {
        assertEquals("a***@example.com", securityUtils.maskEmail("ab@example.com"));
    }

    @Test
    @DisplayName("maskEmail - 无 @ 符号原样返回")
    void maskEmail_noAtSign() {
        assertEquals("notanemail", securityUtils.maskEmail("notanemail"));
    }

    @Test
    @DisplayName("maskEmail - null 返回 null")
    void maskEmail_null() {
        assertNull(securityUtils.maskEmail(null));
    }

    @Test
    @DisplayName("maskIdCard - 标准身份证脱敏")
    void maskIdCard_standard() {
        assertEquals("1101********2345", securityUtils.maskIdCard("110123456789012345"));
    }

    @Test
    @DisplayName("maskIdCard - 短号码原样返回")
    void maskIdCard_short() {
        assertEquals("12345", securityUtils.maskIdCard("12345"));
    }

    @Test
    @DisplayName("maskIdCard - null 返回 null")
    void maskIdCard_null() {
        assertNull(securityUtils.maskIdCard(null));
    }

    @Test
    @DisplayName("maskBankCard - 标准银行卡脱敏")
    void maskBankCard_standard() {
        assertEquals("**** **** **** 3456", securityUtils.maskBankCard("1234567890123456"));
    }

    @Test
    @DisplayName("maskBankCard - 短卡号原样返回")
    void maskBankCard_short() {
        assertEquals("1234567", securityUtils.maskBankCard("1234567"));
    }

    @Test
    @DisplayName("maskBankCard - null 返回 null")
    void maskBankCard_null() {
        assertNull(securityUtils.maskBankCard(null));
    }

    @Test
    @DisplayName("maskName - 标准姓名脱敏")
    void maskName_standard() {
        assertEquals("张**", securityUtils.maskName("张三丰"));
    }

    @Test
    @DisplayName("maskName - 两字姓名脱敏")
    void maskName_twoChars() {
        assertEquals("张*", securityUtils.maskName("张三"));
    }

    @Test
    @DisplayName("maskName - 单字原样返回")
    void maskName_singleChar() {
        assertEquals("张", securityUtils.maskName("张"));
    }

    @Test
    @DisplayName("maskName - null 返回 null")
    void maskName_null() {
        assertNull(securityUtils.maskName(null));
    }

    // ===== mask (通用脱敏) =====

    @Test
    @DisplayName("mask - PHONE 类型")
    void mask_phone() {
        assertEquals("138****1234", securityUtils.mask("13812341234", "PHONE"));
    }

    @Test
    @DisplayName("mask - MOBILE 类型")
    void mask_mobile() {
        assertEquals("138****1234", securityUtils.mask("13812341234", "MOBILE"));
    }

    @Test
    @DisplayName("mask - EMAIL 类型")
    void mask_email() {
        assertEquals("abc***@example.com", securityUtils.mask("abcdef@example.com", "EMAIL"));
    }

    @Test
    @DisplayName("mask - ID_CARD 类型")
    void mask_idCard() {
        assertEquals("1101********2345", securityUtils.mask("110123456789012345", "ID_CARD"));
    }

    @Test
    @DisplayName("mask - ID_NUMBER 类型")
    void mask_idNumber() {
        assertEquals("1101********2345", securityUtils.mask("110123456789012345", "ID_NUMBER"));
    }

    @Test
    @DisplayName("mask - BANK_CARD 类型")
    void mask_bankCard() {
        assertEquals("**** **** **** 3456", securityUtils.mask("1234567890123456", "BANK_CARD"));
    }

    @Test
    @DisplayName("mask - CARD_NO 类型")
    void mask_cardNo() {
        assertEquals("**** **** **** 3456", securityUtils.mask("1234567890123456", "CARD_NO"));
    }

    @Test
    @DisplayName("mask - NAME 类型")
    void mask_name() {
        assertEquals("张**", securityUtils.mask("张三丰", "NAME"));
    }

    @Test
    @DisplayName("mask - REAL_NAME 类型")
    void mask_realName() {
        assertEquals("张**", securityUtils.mask("张三丰", "REAL_NAME"));
    }

    @Test
    @DisplayName("mask - 未知类型走通用脱敏")
    void mask_unknownType() {
        String result = securityUtils.mask("testdata", "UNKNOWN");
        assertEquals("t******a", result);
    }

    @Test
    @DisplayName("mask - type 为 null 走通用脱敏")
    void mask_nullType() {
        String result = securityUtils.mask("testdata", null);
        assertEquals("t******a", result);
    }

    @Test
    @DisplayName("mask - data 为 null 返回 null")
    void mask_nullData() {
        assertNull(securityUtils.mask(null, "PHONE"));
    }

    @Test
    @DisplayName("mask - 通用脱敏 2 字符")
    void mask_generic_twoChars() {
        assertEquals("a*", securityUtils.mask("ab", "UNKNOWN"));
    }

    @Test
    @DisplayName("mask - 通用脱敏 1 字符")
    void mask_generic_oneChar() {
        assertEquals("a*", securityUtils.mask("a", "UNKNOWN"));
    }

    @Test
    @DisplayName("mask - 大写类型")
    void mask_uppercaseType() {
        assertEquals("138****1234", securityUtils.mask("13812341234", "phone"));
    }

    // ===== secureEquals =====

    @Test
    @DisplayName("secureEquals - 相同字符串返回 true")
    void secureEquals_same() {
        assertTrue(securityUtils.secureEquals("abc", "abc"));
    }

    @Test
    @DisplayName("secureEquals - 不同字符串返回 false")
    void secureEquals_different() {
        assertFalse(securityUtils.secureEquals("abc", "def"));
    }

    @Test
    @DisplayName("secureEquals - 两个 null 返回 true")
    void secureEquals_bothNull() {
        assertTrue(securityUtils.secureEquals(null, null));
    }

    @Test
    @DisplayName("secureEquals - 一个 null 返回 false")
    void secureEquals_oneNull() {
        assertFalse(securityUtils.secureEquals("abc", null));
        assertFalse(securityUtils.secureEquals(null, "abc"));
    }

    // ===== generateSecureToken =====

    @Test
    @DisplayName("generateSecureToken - 返回非空 token")
    void generateSecureToken_success() {
        String token = securityUtils.generateSecureToken(32);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("generateSecureToken - 不同调用产生不同 token")
    void generateSecureToken_unique() {
        String token1 = securityUtils.generateSecureToken(32);
        String token2 = securityUtils.generateSecureToken(32);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("generateSecureToken - 长度为 0 返回空字符串")
    void generateSecureToken_zeroLength() {
        String token = securityUtils.generateSecureToken(0);

        assertNotNull(token);
    }

    // ===== encryptionEnabled = false =====

    @Test
    @DisplayName("encrypt - 加密 disabled 时原样返回")
    void encrypt_disabled() throws Exception {
        Field encryptionEnabledField = SecurityUtils.class.getDeclaredField("encryptionEnabled");
        encryptionEnabledField.setAccessible(true);
        encryptionEnabledField.set(securityUtils, false);

        assertEquals("plaintext", securityUtils.encrypt("plaintext"));
    }

    @Test
    @DisplayName("decrypt - 加密 disabled 时原样返回")
    void decrypt_disabled() throws Exception {
        Field encryptionEnabledField = SecurityUtils.class.getDeclaredField("encryptionEnabled");
        encryptionEnabledField.setAccessible(true);
        encryptionEnabledField.set(securityUtils, false);

        assertEquals("ciphertext", securityUtils.decrypt("ciphertext"));
    }

    @Test
    @DisplayName("isEncryptionEnabled - disabled 时返回 false")
    void isEncryptionEnabled_false() throws Exception {
        Field encryptionEnabledField = SecurityUtils.class.getDeclaredField("encryptionEnabled");
        encryptionEnabledField.setAccessible(true);
        encryptionEnabledField.set(securityUtils, false);

        assertFalse(securityUtils.isEncryptionEnabled());
    }

    @Test
    @DisplayName("encrypt - 非法算法抛 RuntimeException")
    void encrypt_invalidAlgorithm() throws Exception {
        Field algorithmField = SecurityUtils.class.getDeclaredField("algorithm");
        algorithmField.setAccessible(true);
        algorithmField.set(securityUtils, "INVALID/ALGORITHM");

        assertThrows(RuntimeException.class, () -> securityUtils.encrypt("test"));
    }

    @Test
    @DisplayName("decrypt - 非法数据抛 RuntimeException")
    void decrypt_invalidData() {
        assertThrows(RuntimeException.class, () -> securityUtils.decrypt("not-valid-base64-encrypted-data!!!"));
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
