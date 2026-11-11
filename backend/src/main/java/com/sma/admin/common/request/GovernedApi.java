package com.sma.admin.common.request;

import java.lang.annotation.*;

/** Reviewed controller. Local writes are the default; other semantics must be explicitly justified. */
@Target({ElementType.TYPE,ElementType.METHOD}) @Retention(RetentionPolicy.RUNTIME) @Inherited @Documented
public @interface GovernedApi {
    String group();
    /** Owner-scoped object (path variables still distinguish different objects). Method overrides require a marked controller. */
    boolean ownResource() default false;
    enum Mode { LOCAL, QUERY, NATIVE }
    Mode mode() default Mode.LOCAL;
    /** Required for QUERY/NATIVE: identifies the reviewed read-only or module-owned execution protocol. */
    String reason() default "";
    int readLimit() default 240;
    int writeLimit() default 60;
}
