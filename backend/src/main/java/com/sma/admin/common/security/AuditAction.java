package com.sma.admin.common.security;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface AuditAction {
 String value();
 String path() default "";
}
