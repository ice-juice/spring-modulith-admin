package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;

import com.sma.admin.common.internal.service.BusinessLedgerResourceFactoryService;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.request.BusinessOutboxConsumer;
import com.sma.admin.common.request.BusinessRevisionRollup;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real source-local event delivery, unknown retry and second-resource isolation. */
@EnabledIfEnvironmentVariable(named = "SMA_P10_SECOND_BUSINESS_DB_URL", matches = ".+")
class BusinessOutboxDeliveryMysqlTests {
    @Test void failureRetainsIntentAndRetryUsesTheOriginalTenantAndBytes() {
        var firstUrl = System.getenv("SMA_P11_BUSINESS_DB_URL");
        var secondUrl = System.getenv("SMA_P10_SECOND_BUSINESS_DB_URL");
        if (firstUrl == null || !URI.create(firstUrl.substring(5)).getPath()
                .equals("/sma_test_p11_x1b_business_20261112")
                || secondUrl == null || !URI.create(secondUrl.substring(5)).getPath()
                .equals("/sma_test_p11_x1a_20261112"))
            throw new IllegalArgumentException("S7d requires exact disposable business schemas");
        try (var platform = pool(com.sma.admin.support.TestDatabase.URL); var first = pool(firstUrl); var second = pool(secondUrl)) {
            var f1 = Flyway.configure().dataSource(first).locations("classpath:db/business-ledger")
                    .table("flyway_schema_history_business_ledger").cleanDisabled(false).load();
            var f2 = Flyway.configure().dataSource(second).locations("classpath:db/business-ledger")
                    .table("flyway_schema_history_business_ledger").cleanDisabled(false).load();
            f1.clean(); f1.migrate(); f1.validate(); f2.clean(); f2.migrate(); f2.validate();
            var firstSql = new JdbcTemplate(first); var secondSql = new JdbcTemplate(second);
            long tenant = java.util.concurrent.ThreadLocalRandom.current().nextLong(80_000_000L, 90_000_000L);
            var uniqueKey = "delivery-" + java.util.UUID.randomUUID().toString().replace("-", "");
            firstSql.update("DELETE FROM common_business_outbox WHERE tenant_id=?", tenant);
            secondSql.update("DELETE FROM common_business_outbox WHERE tenant_id=?", tenant);
            var persistence = new PersistenceConfiguration();
            var factory = new BusinessLedgerResourceFactoryService(new RequestGovernanceProperties(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "v1", "", 100000L, 8),
                    persistence.mybatisPlusInterceptor(), persistence.auditFields(),null,
                    new com.sma.admin.common.internal.persistence.DatabaseIdentifierGenerator(platform));
            var resource = factory.preparePhysical("delivery-first", 1, first);
            var other = factory.preparePhysical("delivery-second", 1, second);
            var actor = Users.principal(997123, tenant, DataAccess.none());
            Users.as(actor, () -> {
                try (var session = resource.open(() -> {})) {
                    var outcome = session.executeLocal(new com.sma.admin.common.request.RequestCommand("shop.outbox", "shop.order",
                            "order-87110011", uniqueKey, new byte[]{1}, ""),
                            new com.sma.admin.common.request.RequestExecution.Work<Long>() {
                                @Override public void authorize() {}
                                @Override public Long execute() {
                                    return session.enqueue("shop.order.created", uniqueKey,
                                            "original".getBytes(StandardCharsets.UTF_8));
                                }
                            });
                    assertThat(outcome.receipt().state()).isEqualTo(
                            com.sma.admin.common.request.RequestReceipt.State.SUCCEEDED);
                }
                return null;
            });
            assertThat(firstSql.queryForObject("SELECT COUNT(*) FROM common_business_outbox WHERE tenant_id=?", Long.class, tenant)).isEqualTo(1);
            assertThat(secondSql.queryForObject("SELECT COUNT(*) FROM common_business_outbox WHERE tenant_id=?", Long.class, tenant)).isZero();
            var calls = new AtomicInteger();
            BusinessOutboxConsumer consumer = new BusinessOutboxConsumer() {
                @Override public boolean supports(String eventType) { return eventType.equals("shop.order.created"); }
                @Override public void deliver(Delivery event) {
                    assertThat(ActorContext.tenantId()).isEqualTo(tenant);
                    assertThat(event.resourceId()).isEqualTo("delivery-first");
                    assertThat(new String(event.payload(), StandardCharsets.UTF_8)).isEqualTo("original");
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("delivery result unknown");
                }
            };
            assertThat(firstSql.queryForObject("SELECT state FROM common_business_outbox WHERE tenant_id=?",
                    String.class, tenant)).isEqualTo("PENDING");
            assertThat(other.deliverOne(List.of(consumer))).isFalse();
            assertThatThrownBy(() -> resource.deliverOne(List.of(consumer))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unknown");
            assertThat(firstSql.queryForObject("SELECT state FROM common_business_outbox WHERE tenant_id=?", String.class, tenant))
                    .isEqualTo("UNKNOWN");
            firstSql.update("UPDATE common_business_outbox SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE tenant_id=?", tenant);
            assertThat(resource.deliverOne(List.of(consumer))).isTrue();
            assertThat(firstSql.queryForObject("SELECT state FROM common_business_outbox WHERE tenant_id=?", String.class, tenant))
                    .isEqualTo("DONE");
            assertThat(calls.get()).isEqualTo(2);
            var rollup = new BusinessRevisionRollup(Clock.systemUTC(), Duration.ofSeconds(10), 16);
            Users.as(actor, () -> {
                assertThat(rollup.sample("delivery-first", 1, "shop.items", () -> 3L).stale()).isFalse();
                assertThat(rollup.sample("delivery-first", 1, "shop.items", () -> {
                    throw new IllegalStateException("physical resource unavailable");
                }).stale()).isTrue();
                assertThat(rollup.observe("delivery-first", 1, "shop.items").revision()).isEqualTo(3);
                assertThat(rollup.observe("delivery-second", 1, "shop.items").stale()).isTrue();
                return null;
            });
        }
    }

    private static HikariDataSource pool(String url) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        config.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        config.setMaximumPoolSize(3); config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }
}
