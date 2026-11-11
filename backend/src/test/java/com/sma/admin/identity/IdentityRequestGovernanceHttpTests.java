package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.identity.internal.service.IdentityRealmService;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class IdentityRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired IdentityRealmService realms;
    @Test void repeatedDisableAdvancesAuthorityOnlyOnceAndForeignTenantCannotAlterIt()throws Exception{
        var actor=new SmaPrincipal(101,"manager",79911,"admin",null,false,DataAccess.unrestricted(),Set.of("identity:realm:manage","identity:realm:view"),"UTC");
        long id=Users.as(actor,()->realms.create(new IdentityRealmService.Input("realm","Realm",true,false,false)).id());
        String key=UUID.randomUUID().toString();
        var first=mvc.perform(post("/api/identity/realms/"+id+"/disable").header("Idempotency-Key",key).contentType("application/json").content("{\"expectedVersion\":1}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
        var repeated=mvc.perform(post("/api/identity/realms/"+id+"/disable").header("Idempotency-Key",key).contentType("application/json").content("{\"expectedVersion\":1}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(repeated.getStatus()).isEqualTo(409);
        Users.as(actor,()->{assertThat(realms.get(id).version()).isEqualTo(2);assertThat(realms.get(id).securityVersion()).isEqualTo(2);return null;});
        var foreign=new SmaPrincipal(101,"manager",79912,"admin",null,false,DataAccess.unrestricted(),actor.permissions(),"UTC");
        var denied=mvc.perform(post("/api/identity/realms/"+id+"/disable").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{\"expectedVersion\":2}").with(Users.request(foreign))).andReturn().getResponse();
        assertThat(json.readTree(denied.getContentAsString()).at("/code").asInt()).isEqualTo(404);
        assertThat(json.readTree(repeated.getContentAsString()).at("/data/name").isMissingNode()).isTrue();
    }
}
