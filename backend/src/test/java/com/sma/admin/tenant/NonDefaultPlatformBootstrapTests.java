package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

/** Run alone against a fresh, explicitly disposable test database. */
@IntegrationTest
@EnabledIfEnvironmentVariable(named="SMA_P10_NONDEFAULT_BOOTSTRAP", matches="true")
@TestPropertySource(properties={
        "sma.tenant.platform-tenant-id=42",
        "sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]",
        "sma.bootstrap.admin-password=Q70a38aa0f"
})
class NonDefaultPlatformBootstrapTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantDirectoryService directory;

    @Test void readyBootstrapBelongsToConfiguredPlatformTenant() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_tenant WHERE id=42 AND code='platform' AND status='ACTIVE'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_tenant WHERE id=1",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_domain WHERE owner_tenant_id=42 AND status='ACTIVE'",Integer.class)).isEqualTo(3);
        assertThat(directory.resolve("localhost")).get().extracting(entry->entry.tenantId()).isEqualTo(42L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=42 AND username='admin' AND super_admin=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_permission WHERE tenant_id=42 AND permission_key='tenant:storage:provision'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_permission WHERE tenant_id=1",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM config_parameter WHERE tenant_id=1",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM config_dictionary_type WHERE tenant_id=1",Integer.class)).isZero();
        assertThat(TenantScope.current()).isEqualTo(1L); // startup scope must not leak to the test/request thread
    }
}
