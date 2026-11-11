/**
 * 共享内核（shared module）：统一响应、业务异常、安全注解、Web 与序列化基础设施。
 * <p>
 * 约束：不依赖任何业务模块（allowedDependencies 为空）。对外暴露根包（unnamed interface）与
 * {@code security}、{@code persistence}、{@code extension}、{@code cache}、{@code request}、{@code deployment} 命名接口；
 * {@code internal} 下的实现类其他模块不可引用。
 */
@org.springframework.modulith.ApplicationModule(
		displayName = "Common（共享内核）",
		allowedDependencies = {})
package com.sma.admin.common;
