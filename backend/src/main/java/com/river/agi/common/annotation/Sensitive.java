package com.river.agi.common.annotation;

import com.river.agi.common.serializer.SensitiveSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感数据脱敏注解（合同 14.2 个人信息保护）。
 * 标注在 DTO 字段上，Jackson 序列化时按 {@link #type()} 自动脱敏输出。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /**
     * 脱敏类型
     */
    Type type();

    /**
     * 脱敏类型枚举
     */
    enum Type {
        /** 手机号：138****8888 */
        PHONE,
        /** 邮箱：zha***@example.com */
        EMAIL,
        /** 身份证：1101**********1234 */
        ID_CARD,
        /** 银行卡：**** **** **** 8888 */
        BANK_CARD,
        /** 姓名：张** */
        NAME,
        /** 通用：保留首尾各1字符 */
        GENERIC
    }
}
