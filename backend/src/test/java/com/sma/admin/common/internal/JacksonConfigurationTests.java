package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class JacksonConfigurationTests {

	@Test
	void writesLongValuesAsStrings() {
		JsonMapper.Builder builder = JsonMapper.builder();
		new JacksonConfiguration().longValuesAsStrings().customize(builder);

		String json = builder.build().writeValueAsString(Map.of("id", 2119988795651796992L));

		assertThat(json).isEqualTo("{\"id\":\"2119988795651796992\"}");
	}

}
