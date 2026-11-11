package com.sma.admin.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 后台人类登录即可访问（对应 JBolt 的 {@code @UnCheck}），不接受应用用户或机器身份。
 * 应用用户接口使用 {@link PermitApplicationUser}，并保留对象/租户授权。
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("T(com.sma.admin.common.security.ActorContext).isAdminUser(authentication)")
public @interface PermitLoggedIn {

}
