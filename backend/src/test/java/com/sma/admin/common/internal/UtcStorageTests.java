package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class UtcStorageTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void databaseSessionsRunInUtc() {
		Long offsetSeconds = jdbc.queryForObject("select timestampdiff(second, utc_timestamp(), now())", Long.class);
		assertThat(offsetSeconds).isZero();
	}

}
