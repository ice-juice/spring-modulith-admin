package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.SpringbootModulithAdminApplication;
import com.sma.admin.common.internal.cache.CacheNodeRuntime;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest @AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties={"sma.deployment.schema-mode=validate","sma.deployment.evidence.enabled=true",
        "sma.cache.nodes.enabled=true","sma.cache.nodes.cluster=p08-runtime-evidence","sma.cache.nodes.id=a","sma.cache.nodes.expected=a,b",
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.common.credentials.key-file=src/test/resources/deployment-evidence-test.keys",
        "sma.openapi.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@TestExecutionListeners(listeners=DeploymentEvidenceRuntimeHttpTests.PrepareSchema.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class DeploymentEvidenceRuntimeHttpTests {
    @Autowired DeploymentEvidenceRuntime runtime;
    @Autowired CacheNodeRuntime membership;
    @Autowired MockMvc mvc;
    @MockitoSpyBean DeploymentEvidenceRegistry registry;

    /** After guarded test cleanup, before the serving application starts in validate mode. */
    public static class PrepareSchema implements TestExecutionListener,Ordered {
        @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE+1;}
        @Override public void beforeTestClass(TestContext context){
            try(var migrated=MigrationApplication.application(ModuleSchemaOperations.Mode.MIGRATE,SpringbootModulithAdminApplication.class)
                    .run("--spring.profiles.active=test")){migrated.getBean("controlledSchemaOperation");}
        }
    }

    @Test void actualSchedulerPublishesEvidenceAndHealthRejectsMismatchWithoutStoppingLease()throws Exception{
        await().atMost(Duration.ofSeconds(15)).until(()->runtime.status().matches());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        doReturn(new DeploymentEvidenceRegistry.Snapshot("f".repeat(64),Set.of())).when(registry).collect(any());
        try{
            await().atMost(Duration.ofSeconds(10)).until(()->runtime.status().state().equals("MISMATCH"));
            mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN"));
            assertThat(membership.currentLease()).isNotNull();assertThat(membership.status().state()).isEqualTo("OWNED");
            doReturn(new DeploymentEvidenceRegistry.Snapshot(null,Set.of("auth.security-keys"))).when(registry).collect(any());
            await().atMost(Duration.ofSeconds(10)).until(()->runtime.status().state().equals("UNAVAILABLE"));
        }finally{doCallRealMethod().when(registry).collect(any());}
        await().atMost(Duration.ofSeconds(10)).until(()->runtime.status().matches());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
