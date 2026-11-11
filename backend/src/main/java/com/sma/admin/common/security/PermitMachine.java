package com.sma.admin.common.security;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Machine authentication only; the owning API still enforces client capabilities and target scope. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("T(com.sma.admin.common.security.ActorContext).isMachine(authentication)")
public @interface PermitMachine {}
