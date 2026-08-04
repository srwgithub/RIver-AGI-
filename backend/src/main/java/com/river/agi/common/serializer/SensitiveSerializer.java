package com.river.agi.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.river.agi.common.annotation.Sensitive;

import java.io.IOException;

/**
 * 敏感数据脱敏 Jackson 序列化器（合同 14.2 个人信息保护）。
 * 由 {@link Sensitive} 注解驱动，序列化时读取字段上的注解类型并对字符串脱敏输出。
 */
public class SensitiveSerializer extends StdSerializer<String>
        implements ContextualSerializer {

    private Sensitive.Type type;

    public SensitiveSerializer() {
        super(String.class);
        this.type = null;
    }

    public SensitiveSerializer(Sensitive.Type type) {
        super(String.class);
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(mask(value, type));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(Sensitive.class);
        }
        if (annotation != null) {
            return new SensitiveSerializer(annotation.type());
        }
        return this;
    }

    /**
     * 静态脱敏工具方法，供序列化器与业务代码复用。
     */
    public static String mask(String value, Sensitive.Type type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (type == null) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case NAME -> maskName(value);
            case GENERIC -> maskGeneric(value);
        };
    }

    static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return repeat('*', phone.length());
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return repeat('*', email.length());
        }
        String prefix = email.substring(0, Math.min(3, atIndex));
        String suffix = email.substring(atIndex);
        return prefix + "***" + suffix;
    }

    static String maskIdCard(String idCard) {
        if (idCard.length() < 10) {
            return repeat('*', idCard.length());
        }
        return idCard.substring(0, 4) + repeat('*', idCard.length() - 8) + idCard.substring(idCard.length() - 4);
    }

    static String maskBankCard(String card) {
        if (card.length() < 8) {
            return repeat('*', card.length());
        }
        return "**** **** **** " + card.substring(card.length() - 4);
    }

    static String maskName(String name) {
        if (name.length() < 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + repeat('*', name.length() - 1);
    }

    static String maskGeneric(String value) {
        if (value.length() <= 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + repeat('*', value.length() - 2) + value.charAt(value.length() - 1);
    }

    private static String repeat(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
