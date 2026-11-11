package com.sma.admin.common.security;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Exact application audience, in addition to tenant/object checks performed by the service. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("T(com.sma.admin.common.security.ActorContext).isApplicationUser(authentication, '{value}')")
public @interface PermitApplicationUser {
    String value();
}
