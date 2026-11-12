/**
 * 认证与会话：登录、服务端令牌、在线用户、锁屏、安全过滤链。
 */
@org.springframework.modulith.ApplicationModule(
		displayName = "Auth（认证与会话）",
        allowedDependencies = { "common", "common :: security", "common :: persistence", "common :: request", "common :: cache", "common :: deployment", "iam", "config" })
package com.sma.admin.auth;
