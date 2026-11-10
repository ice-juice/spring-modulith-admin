package com.sma.admin.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 无需登录即可访问（对应 JBolt 的 {@code @Clear}），例如登录、验证码。
 * <p>
 * 方法上的声明只解决方法级授权；对应 URL 还必须在 auth 模块的公开路径清单中放行。
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("permitAll()")
public @interface PermitAnonymous {

}
