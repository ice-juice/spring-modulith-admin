package com.sma.admin.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 要求当前用户拥有指定权限 key（对应 JBolt 的 {@code @CheckPermission}）。
 * <p>
 * 权限 key 格式为 {@code <模块>:<资源>[:<动作>]}，例如 {@code iam:user:create}，必须以所在模块 id 开头。
 * 用户的权限 key 在登录时作为 authority 加载，超级管理员的默认权限也在那时合并。
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("T(com.sma.admin.common.security.ActorContext).isAdminUser(authentication) && hasAuthority('{value}')")
public @interface RequiresPermission {

	/**
	 * 权限 key，例如 {@code iam:user:create}。
	 */
	String value();

}
