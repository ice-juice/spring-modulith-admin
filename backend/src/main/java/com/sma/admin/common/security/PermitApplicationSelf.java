package com.sma.admin.common.security;
import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Reserved for reviewed platform self-service endpoints; business endpoints must declare an exact audience. */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
@PreAuthorize("T(com.sma.admin.common.security.ActorContext).isApplicationPrincipal(authentication)")
public @interface PermitApplicationSelf {}
