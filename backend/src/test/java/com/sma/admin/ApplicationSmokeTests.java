package com.sma.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.sma.admin.support.IntegrationTest;

@IntegrationTest
@AutoConfigureMockMvc
class ApplicationSmokeTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void runsAgainstTheMigratedTestDatabase() {
		assertThat(jdbc.queryForObject("select database()", String.class)).isEqualTo("springboot_modulith_admin_test");
		Integer eventTable = jdbc.queryForObject(
				"select count(*) from information_schema.tables where table_schema = database() and upper(table_name) = 'EVENT_PUBLICATION'",
				Integer.class);
		assertThat(eventTable).isEqualTo(1);
	}

	@Test
	void healthIsPublicAndApiRequiresAuthentication() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
		assertThat(mvc.get().uri("/api/iam/users")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
