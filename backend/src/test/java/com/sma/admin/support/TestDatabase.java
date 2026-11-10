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
        ensureSafeDatabase(URL);
		Flyway.configure().dataSource(URL, USERNAME, PASSWORD).cleanDisabled(false).load().clean();
	}
    static void ensureSafeDatabase(String url) {
        if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Tests require an explicitly named MySQL test database");
        String path=java.net.URI.create(url.substring(5)).getPath();
        if(path==null||!path.matches("/(springboot_modulith_admin_test|sma_test_[a-z0-9_]+)")) {
            throw new IllegalArgumentException("Refusing Flyway clean outside the dedicated test database");
        }
    }

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

}
