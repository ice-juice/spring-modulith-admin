package com.sma.admin.support;

import org.flywaydb.core.Flyway;

/**
 * Connection settings of the integration test database. Defaults match {@code application-test.yml};
 * override with the environment variables {@code SMA_TEST_DB_URL}, {@code SMA_TEST_DB_USERNAME}, {@code SMA_TEST_DB_PASSWORD}.
 */
public final class TestDatabase {

	public static final String URL = env("SMA_TEST_DB_URL",
			"jdbc:mysql://127.0.0.1:3306/springboot_modulith_admin_test?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true");

	public static final String USERNAME = env("SMA_TEST_DB_USERNAME", "springboot_modulith_admin");

	public static final String PASSWORD = env("SMA_TEST_DB_PASSWORD", "sma_dev");

	private TestDatabase() {
	}

	/**
	 * Drops every object in the test schema. The application's own Flyway run recreates it afterwards.
	 */
	public static void clean() {
		Flyway.configure().dataSource(URL, USERNAME, PASSWORD).cleanDisabled(false).load().clean();
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

}
