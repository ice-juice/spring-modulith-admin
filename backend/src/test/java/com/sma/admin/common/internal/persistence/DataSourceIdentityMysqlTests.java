package com.sma.admin.common.internal.persistence;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.util.List;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.sma.admin.common.internal.SqlTelemetry;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.security.TenantScope;

/** Explicit task-owned physical MySQL schemas; no application startup, Flyway clean or other database writes. */
@EnabledIfEnvironmentVariable(named = "SMA_P10_JDBC_DB_B", matches = ".+")
class DataSourceIdentityMysqlTests {
    private static final String ID = "com.sma.business.fixture.internal.persistence.ProbeMapper.insert";
    private static final String TABLE = "common_p10_connection_probe";

    @Test void twoRealMysqlPoolsExecuteSameMapperInSeparateSchemasAndKeepActualIdentity() throws Exception {
        String a = requireTestUrl(System.getenv("SMA_TEST_DB_URL"));
        String b = requireTestUrl(System.getenv("SMA_P10_JDBC_DB_B"));
        assertThat(URI.create(a.substring(5)).getPath()).isNotEqualTo(URI.create(b.substring(5)).getPath());
        var telemetry = new SqlTelemetry(0);
        try (var poolA = pool(a); var poolB = pool(b)) {
            var sourceA = new IdentifiedDataSource(poolA, new DataSourceIdentity("business-a", 1));
            var sourceB = new IdentifiedDataSource(poolB, new DataSourceIdentity("business-b", 3));
            for (var pool : List.of(poolA,poolB)) try (var connection = pool.getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (id BIGINT PRIMARY KEY, marker VARCHAR(32) NOT NULL)");
                sql.executeUpdate("DELETE FROM " + TABLE);
            }
            var factoryA = factory(sourceA, telemetry, "a");
            var factoryB = factory(sourceB, telemetry, "b");
            TenantScope.within(712, () -> {
                try (var session = factoryA.openSession()) { session.insert(ID); session.commit(); }
                try (var session = factoryB.openSession()) { session.insert(ID); session.commit(); }
                assertThatThrownBy(() -> { try (var session = factoryA.openSession()) { session.insert(ID); } })
                        .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
                var rows = telemetry.statements();
                assertThat(rows).hasSize(2);
                assertThat(rows).filteredOn(row -> row.resourceId().equals("business-a")).singleElement().satisfies(row -> {
                    assertThat(row.calls()).isEqualTo(2); assertThat(row.errors()).isEqualTo(1); assertThat(row.resourceRevision()).isEqualTo(1);
                });
                assertThat(rows).filteredOn(row -> row.resourceId().equals("business-b")).singleElement().satisfies(row -> {
                    assertThat(row.calls()).isEqualTo(1); assertThat(row.errors()).isZero(); assertThat(row.resourceRevision()).isEqualTo(3);
                });
                return null;
            });
            assertThat(TenantScope.within(713, telemetry::statements)).isEmpty();
            for (var entry : java.util.Map.of(poolA,"a",poolB,"b").entrySet()) {
                try (var connection = entry.getKey().getConnection(); var sql = connection.createStatement();
                        var rows = sql.executeQuery("SELECT marker FROM " + TABLE)) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(entry.getValue());
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private static String requireTestUrl(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_[a-z0-9_]+")) {
            throw new IllegalArgumentException("P10 JDBC verification requires isolated sma_test_p10 schemas");
        }
        return url;
    }
    private static HikariDataSource pool(String url) {
        var config = new HikariConfig(); config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"));
        config.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev"));
        config.setMaximumPoolSize(2); config.setMinimumIdle(0); config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }
    private static SqlSessionFactory factory(IdentifiedDataSource source, SqlTelemetry telemetry, String marker) {
        var configuration = new Configuration(new Environment(marker,new JdbcTransactionFactory(),source));
        configuration.addInterceptor(telemetry);
        configuration.addMappedStatement(new MappedStatement.Builder(configuration,ID,
                new StaticSqlSource(configuration,"INSERT INTO " + TABLE + " (id,marker) VALUES (1,'" + marker + "')"),SqlCommandType.INSERT).build());
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
