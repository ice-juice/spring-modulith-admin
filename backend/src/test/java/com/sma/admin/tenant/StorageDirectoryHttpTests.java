package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sma.admin.common.security.DataAccess;
import com.sma.admin.common.security.SmaPrincipal;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sma.tenant.platform-admin-user-ids=991",
        "sma.tenant.storage.allowed-hosts=127.0.0.1",
        "sma.tenant.storage.allow-insecure-loopback=true",
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=100ms"
})
class StorageDirectoryHttpTests {
    private static final Set<String> ALL = Set.of("tenant:storage:view", "tenant:storage:register", "tenant:storage:bind",
            "tenant:storage:provision", "tenant:storage:switch");
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired TenantDirectoryService tenants;

    @Test void platformIdentityPermissionsAndSafeProjectionGateDirectory() throws Exception {
        var operator = actor(991, 1, ALL);
        var viewOnly = actor(991, 1, Set.of("tenant:storage:view"));
        assertThat(send(get("/api/tenant/storage-resources"), actor(992, 1, ALL)).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/tenant/storage-resources"), actor(991, 2, ALL)).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/tenant/storage-resources"), actor(991, 1, Set.of())).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/tenant/storage-bindings").param("tenantId", "1"), operator).getStatus()).isEqualTo(200);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String resourceId = "s6d-" + suffix;
        String secretRef = "secret/storage-" + suffix;
        String resource = json.writeValueAsString(Map.of(
                "resourceId", resourceId, "revision", 1, "displayName", "S6d HTTP fixture",
                "host", "127.0.0.1", "port", 3306, "databaseName", "sma_test_p11_x1b_business_20261112",
                "tlsMode", "DISABLED", "secretRef", secretRef, "poolMax", 2, "connectTimeoutMillis", 1000));
        assertThat(send(post("/api/tenant/storage-resources").content(resource), viewOnly).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/tenant/storage-resources").content(resource), operator).getStatus()).isEqualTo(400);
        String registerKey = UUID.randomUUID().toString();
        var created = send(post("/api/tenant/storage-resources").header("Idempotency-Key", registerKey)
                .content(resource), operator);
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(created.getContentAsString()).at("/data/state").asText()).isEqualTo("REGISTERED");
        assertThat(created.getContentAsString()).doesNotContain(secretRef).doesNotContain("secretRef");
        assertThat(send(post("/api/tenant/storage-resources").header("Idempotency-Key", registerKey)
                .content(resource), operator).getStatus()).isEqualTo(409);
        var listed = send(get("/api/tenant/storage-resources").param("resourceId", resourceId), viewOnly);
        assertThat(listed.getStatus()).isEqualTo(200);
        assertThat(listed.getContentAsString()).contains(resourceId).doesNotContain(secretRef).doesNotContain("secretRef");

        long tenantId = Users.as(operator, () -> {
            var tenant = tenants.create(new TenantDirectoryService.TenantInput("s6d-tenant-" + suffix, "S6d fixture"));
            tenants.changeStatus(tenant.id(), new TenantDirectoryService.StatusInput("ACTIVE", 1));
            return tenant.id();
        });
        String binding = json.writeValueAsString(Map.of("ownerTenantId", tenantId, "storageGroup", "shop",
                "resourceId", resourceId, "resourceRevision", 1, "schemaCompatibility", "v1"));
        assertThat(send(post("/api/tenant/storage-bindings").header("Idempotency-Key", UUID.randomUUID().toString())
                .content(binding), viewOnly).getStatus()).isEqualTo(403);
        var bound = send(post("/api/tenant/storage-bindings").header("Idempotency-Key", UUID.randomUUID().toString())
                .content(binding), operator);
        assertThat(bound.getStatus()).as(bound.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(bound.getContentAsString()).at("/data/state").asText()).isEqualTo("PENDING");
        var bindings = send(get("/api/tenant/storage-bindings").param("tenantId", Long.toString(tenantId)), viewOnly);
        assertThat(bindings.getStatus()).isEqualTo(200);
        assertThat(bindings.getContentAsString()).contains(resourceId).doesNotContain(secretRef);
        assertThat(send(get("/api/tenant/storage-bindings").param("tenantId", Long.toString(tenantId)),
                actor(991, tenantId, ALL)).getStatus()).isEqualTo(403);

        String invalid = resource.replace("127.0.0.1", "169.254.169.254").replace(resourceId, "bad-" + suffix);
        var rejected = send(post("/api/tenant/storage-resources").header("Idempotency-Key", UUID.randomUUID().toString())
                .content(invalid), operator);
        assertThat(rejected.getStatus()).as(rejected.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(rejected.getContentAsString()).at("/code").asInt()).isNotZero();
        assertThat(rejected.getContentAsString()).doesNotContain(secretRef);
    }

    @Test void nativeProvisionReceiptRequiresDeploymentIdentityAndExplicitPermission() throws Exception {
        var operator = actor(991, 1, ALL);
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var resourceId = "s7d-" + suffix;
        var resource = json.writeValueAsString(Map.of(
                "resourceId", resourceId, "revision", 1, "displayName", "S7d HTTP fixture",
                "host", "127.0.0.1", "port", 3306, "databaseName", "sma_test_p11_x1b_business_20261112",
                "tlsMode", "DISABLED", "secretRef", "secret/s7d-" + suffix,
                "poolMax", 2, "connectTimeoutMillis", 1000));
        var registered = send(post("/api/tenant/storage-resources")
                .header("Idempotency-Key", UUID.randomUUID().toString()).content(resource), operator);
        assertThat(registered.getStatus()).as(registered.getContentAsString()).isEqualTo(200);
        long tenantId = Users.as(operator, () -> {
            var tenant = tenants.create(new TenantDirectoryService.TenantInput("s7d-tenant-" + suffix, "S7d fixture"));
            tenants.changeStatus(tenant.id(), new TenantDirectoryService.StatusInput("ACTIVE", 1));
            return tenant.id();
        });
        var binding = json.writeValueAsString(Map.of("ownerTenantId", tenantId, "storageGroup", "shop",
                "resourceId", resourceId, "resourceRevision", 1, "schemaCompatibility", "v1"));
        var bound = send(post("/api/tenant/storage-bindings")
                .header("Idempotency-Key", UUID.randomUUID().toString()).content(binding), operator);
        assertThat(bound.getStatus()).as(bound.getContentAsString()).isEqualTo(200);
        String id = json.readTree(bound.getContentAsString()).at("/data/id").asText();
        var key = UUID.randomUUID().toString();
        var input = json.writeValueAsString(Map.of("bindingId", id, "expectedVersion", 1, "operationKey", key));
        assertThat(send(post("/api/tenant/storage-provisions").content(input),
                actor(991, 1, Set.of("tenant:storage:view"))).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/tenant/storage-provisions").content(input),
                actor(992, 1, ALL)).getStatus()).isEqualTo(403);
        var attempted = send(post("/api/tenant/storage-provisions").content(input), operator);
        assertThat(attempted.getStatus()).as(attempted.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(attempted.getContentAsString()).at("/code").asInt()).isNotZero();
        var receipt = send(get("/api/tenant/storage-provisions/" + key), operator);
        assertThat(receipt.getStatus()).as(receipt.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(receipt.getContentAsString()).at("/data/state").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(send(get("/api/tenant/storage-provisions/" + key),
                actor(991, 1, Set.of("tenant:storage:provision"))).getStatus()).isEqualTo(403);
        assertThat(json.readTree(send(post("/api/tenant/storage-provisions").content(input), operator)
                .getContentAsString()).at("/code").asInt()).isNotZero();
        var switchInput = json.writeValueAsString(Map.of("bindingId", id, "expectedVersion", 1,
                "targetResourceId", resourceId, "targetRevision", 1,
                "operationKey", UUID.randomUUID().toString()));
        assertThat(send(post("/api/tenant/storage-switches").content(switchInput),
                actor(991, 1, Set.of("tenant:storage:view"))).getStatus()).isEqualTo(403);
        var rejectedSwitch = send(post("/api/tenant/storage-switches").content(switchInput), operator);
        assertThat(rejectedSwitch.getStatus()).as(rejectedSwitch.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(rejectedSwitch.getContentAsString()).at("/code").asInt()).isNotZero();
        assertThat(send(get("/api/tenant/storage-provisions/" + key), actor(991, tenantId, ALL)).getStatus())
                .isEqualTo(403);
    }

    private SmaPrincipal actor(long userId, long tenantId, Set<String> permissions) {
        return new SmaPrincipal(userId, "operator", tenantId, "admin", null, false,
                DataAccess.unrestricted(), permissions, "UTC");
    }

    private org.springframework.mock.web.MockHttpServletResponse send(
            MockHttpServletRequestBuilder request, SmaPrincipal actor) throws Exception {
        return mvc.perform(request.contentType("application/json").with(Users.request(actor)))
                .andReturn().getResponse();
    }
}
