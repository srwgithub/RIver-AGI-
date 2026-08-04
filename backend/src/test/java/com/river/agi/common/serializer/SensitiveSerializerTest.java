package com.river.agi.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.river.agi.common.annotation.Sensitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("敏感数据脱敏序列化器测试")
class SensitiveSerializerTest {

    private ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(String.class, new SensitiveSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    @DisplayName("mask - 手机号脱敏")
    void maskPhone() {
        assertEquals("138****8888", SensitiveSerializer.mask("13812348888", Sensitive.Type.PHONE));
    }

    @Test
    @DisplayName("mask - 短手机号全星号")
    void maskPhoneShort() {
        assertEquals("**", SensitiveSerializer.mask("12", Sensitive.Type.PHONE));
    }

    @Test
    @DisplayName("mask - 邮箱脱敏")
    void maskEmail() {
        assertEquals("zha***@example.com", SensitiveSerializer.mask("zhanglei@example.com", Sensitive.Type.EMAIL));
    }

    @Test
    @DisplayName("mask - 无@邮箱全星号")
    void maskEmailNoAt() {
        assertEquals("****", SensitiveSerializer.mask("abcd", Sensitive.Type.EMAIL));
    }

    @Test
    @DisplayName("mask - 身份证脱敏")
    void maskIdCard() {
        String masked = SensitiveSerializer.mask("110101199001011234", Sensitive.Type.ID_CARD);
        assertTrue(masked.startsWith("1101"));
        assertTrue(masked.endsWith("1234"));
        assertTrue(masked.contains("*"));
    }

    @Test
    @DisplayName("mask - 银行卡脱敏")
    void maskBankCard() {
        assertEquals("**** **** **** 7888", SensitiveSerializer.mask("6222021234567888", Sensitive.Type.BANK_CARD));
    }

    @Test
    @DisplayName("mask - 姓名脱敏")
    void maskName() {
        assertEquals("张**", SensitiveSerializer.mask("张三丰", Sensitive.Type.NAME));
    }

    @Test
    @DisplayName("mask - 单字姓名")
    void maskNameSingle() {
        assertEquals("张*", SensitiveSerializer.mask("张", Sensitive.Type.NAME));
    }

    @Test
    @DisplayName("mask - 通用脱敏")
    void maskGeneric() {
        assertEquals("a****z", SensitiveSerializer.mask("abcdez", Sensitive.Type.GENERIC));
    }

    @Test
    @DisplayName("mask - null/空值原样返回")
    void maskNullEmpty() {
        assertNull(SensitiveSerializer.mask(null, Sensitive.Type.PHONE));
        assertEquals("", SensitiveSerializer.mask("", Sensitive.Type.PHONE));
    }

    @Test
    @DisplayName("mask - type 为 null 原样返回")
    void maskNullType() {
        assertEquals("13812348888", SensitiveSerializer.mask("13812348888", null));
    }
}
