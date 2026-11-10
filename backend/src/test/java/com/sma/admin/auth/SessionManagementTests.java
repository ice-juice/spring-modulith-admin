package com.sma.admin.auth;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.support.IntegrationTest;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
class SessionManagementTests {
    @Autowired MockMvcTester mvc;
    @Autowired UserService users;
    @Autowired SessionIssuer issuer;
    @Autowired SessionManager manager;
    @Autowired JsonMapper json;
    @Test
    void lockingProtectsBusinessEndpointsAndForceLogoutRevokesBearerToken() throws Exception {
        users.create(new UserService.Create(new UserService.Input("lock-tester","Lock Tester",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC"),"Test@Secure2026"));
        var user=users.authenticate("lock-tester","Test@Secure2026","admin").orElseThrow();
        String token=issuer.issue(user,false,java.time.Duration.ofHours(8),"127.0.0.1","lock-fixture").token();
        assertThat(mvc.post().uri("/api/auth/lock").header("Authorization","Bearer "+token)).hasStatusOk();
        assertThat(mvc.get().uri("/api/iam/departments").header("Authorization","Bearer "+token)).hasStatus(423);
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization","Bearer "+token)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.locked",value -> value.assertThat().isEqualTo(true));
        assertThat(mvc.post().uri("/api/auth/unlock").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.error",value -> value.assertThat().isEqualTo("auth.error.invalid-credentials"));
        assertThat(mvc.post().uri("/api/auth/unlock").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Test@Secure2026\"}")).hasStatusOk()
                .bodyJson().hasPathSatisfying("$.code",value -> value.assertThat().isEqualTo(0));
        users.bootstrap("Admin@Secure2026");
        var admin=users.authenticate("admin","Admin@Secure2026","admin").orElseThrow();
        String adminToken=issuer.issue(admin,false,java.time.Duration.ofHours(8),"127.0.0.1","admin-fixture").token();
        var online=mvc.get().uri("/api/auth/sessions").param("keyword","lock-tester").header("Authorization","Bearer "+adminToken).exchange();
        assertThat(online).hasStatusOk().bodyJson().doesNotHavePath("$.data.items[0].tokenHash");
        long id=json.readTree(online.getResponse().getContentAsString()).path("data").path("items").get(0).path("id").asLong();
        assertThat(mvc.post().uri("/api/auth/sessions/"+id+"/revoke").header("Authorization","Bearer "+adminToken)).hasStatusOk();
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization","Bearer "+token)).hasStatus(401);
    }
}
