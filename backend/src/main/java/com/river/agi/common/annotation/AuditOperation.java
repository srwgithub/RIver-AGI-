package com.river.agi.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditOperation {
    String action();
    String resourceType();
    String description() default "";
}
