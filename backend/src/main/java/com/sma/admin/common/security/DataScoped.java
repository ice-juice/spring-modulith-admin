package com.sma.admin.common.security;

import java.lang.annotation.*;

/** Restricts every read and write through this mapper to the union of the user's role data grants. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScoped {
    String table();
    String department() default "dept_id";
    String owner() default "created_by";
}
