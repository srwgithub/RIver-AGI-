package com.river.agi.common.aspect;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.EncryptField;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 字段级加解密切面（合同 14.1.1）。
 * 拦截 Mapper 写入方法（insert/update*）对实体 {@link EncryptField} 字段加密，
 * 拦截读取方法（select*）对返回值解密。
 * 当 {@code encryption.enabled=false} 时 {@link SecurityUtils#encrypt}/{@link SecurityUtils#decrypt}
 * 直接透传原值，行为与无加密一致，保证向后兼容。
 */
@Slf4j
@Aspect
@Component
public class EncryptFieldAspect {

    private final SecurityUtils securityUtils;

    public EncryptFieldAspect(SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    @Around("execution(* com.river.agi..mapper.*Mapper.insert*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.update*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.save*(..))")
    public Object aroundWrite(ProceedingJoinPoint point) throws Throwable {
        Object[] args = point.getArgs();
        for (Object arg : args) {
            if (arg != null) {
                encryptObject(arg);
            }
        }
        return point.proceed();
    }

    @Around("execution(* com.river.agi..mapper.*Mapper.select*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.get*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.findById*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.list*(..)) || " +
            "execution(* com.river.agi..mapper.*Mapper.page*(..))")
    public Object aroundRead(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        decryptResult(result);
        return result;
    }

    private void encryptObject(Object target) {
        if (target == null) {
            return;
        }
        if (target instanceof Collection<?> collection) {
            for (Object item : collection) {
                encryptObject(item);
            }
            return;
        }
        for (Field field : collectEncryptFields(target.getClass())) {
            field.setAccessible(true);
            try {
                Object value = field.get(target);
                if (value instanceof String plain && !plain.isEmpty()) {
                    field.set(target, securityUtils.encrypt(plain));
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to encrypt field {} on {}: {}", field.getName(), target.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void decryptResult(Object result) {
        if (result == null) {
            return;
        }
        if (result instanceof Collection<?> collection) {
            for (Object item : collection) {
                decryptResult(item);
            }
            return;
        }
        for (Field field : collectEncryptFields(result.getClass())) {
            field.setAccessible(true);
            try {
                Object value = field.get(result);
                if (value instanceof String cipher && !cipher.isEmpty()) {
                    field.set(result, securityUtils.decrypt(cipher));
                }
            } catch (IllegalAccessException e) {
                log.warn("Failed to decrypt field {} on {}: {}", field.getName(), result.getClass().getSimpleName(), e.getMessage());
            } catch (Exception e) {
                // 解密失败（如非密文）保持原值，避免读取中断
                log.debug("Skip decrypt field {} on {}: {}", field.getName(), result.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private List<Field> collectEncryptFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(EncryptField.class) && field.getType() == String.class) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}
