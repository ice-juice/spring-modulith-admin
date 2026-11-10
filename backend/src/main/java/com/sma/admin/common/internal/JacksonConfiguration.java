package com.sma.admin.common.internal;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * JSON 序列化约定：雪花 ID 超出 JavaScript 安全整数范围，所有 Long 一律输出为字符串。
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {

	@Bean
	JsonMapperBuilderCustomizer longValuesAsStrings() {
		SimpleModule module = new SimpleModule("sma-long-as-string");
		module.addSerializer(Long.class, ToStringSerializer.instance);
		module.addSerializer(Long.TYPE, ToStringSerializer.instance);
		return builder -> builder.addModule(module);
	}

}
