package com.river.agi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级加密注解（合同 14.1.1 数据加密）。
 * 标注在实体的 String 字段上，由 {@link com.river.agi.common.aspect.EncryptFieldAspect}
 * 在 Mapper 写入前自动加密、读取后自动解密。
 * 加密算法 AES/GCM/NoPadding，受 {@code encryption.enabled} 开关控制。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {
}
