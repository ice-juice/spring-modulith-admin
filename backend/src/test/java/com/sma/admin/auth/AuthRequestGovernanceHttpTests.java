package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.auth.internal.service.SessionIssuer;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.minimum-write-interval=60s")
class AuthRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserService users;
    @Autowired SessionIssuer issuer;
    @Test void independentAuthenticatedSessionsDoNotShareCooldownAndHeadersCannotForgeSessionScope() {
        String[] tokens=Users.as(Users.principal(79601,79601,DataAccess.unrestricted()),()->{
            var row=users.create(new UserService.Create(new UserService.Input("governed-sessions","Session test",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC"),"m7K2v9R4"));
            var account=users.findById(row.id()).orElseThrow();
            return new String[]{issuer.issue(account,false,Duration.ofMinutes(10),"127.0.0.1","one").token(),
                    issuer.issue(account,false,Duration.ofMinutes(10),"127.0.0.1","two").token()};
        });
        for(String token:tokens){
            var locked=send(token,post("/api/auth/lock"));success(locked);
            assertThat(locked.getResponse().getHeader("X-Request-Policy")).isEqualTo("NATIVE");
            assertThat(locked.getResponse().getHeader("X-Operation-Id")).isNull();
        }
        for(String token:tokens)success(send(token,post("/api/auth/unlock").contentType("application/json").content("{\"password\":\"m7K2v9R4\"}")));
        var rapid=send(tokens[0],post("/api/auth/unlock").header("sma.session-id","999999")
                .contentType("application/json").content("{\"password\":\"m7K2v9R4\"}"));
        assertThat(rapid.getResponse().getStatus()).as(body(rapid)).isEqualTo(429);
        assertThat(rapid.getResponse().getHeader("Retry-After")).isNotBlank();
        for(String token:tokens){var response=send(token,get("/api/auth/me"));success(response);assertThat(json.readTree(body(response)).at("/data/locked").asBoolean()).isFalse();}
    }
    private MvcResult send(String token,MockHttpServletRequestBuilder request){
        try{return mvc.perform(request.header("Authorization","Bearer "+token).header("Host","t79601-admin.test")
                .with(r->{r.setServerName("t79601-admin.test");return r;})
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext(
                        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext()))).andReturn();}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private String body(MvcResult response){try{return response.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void success(MvcResult response){assertThat(response.getResponse().getStatus()).as(body(response)).isEqualTo(200);assertThat(json.readTree(body(response)).path("code").asInt()).as(body(response)).isZero();}
}
