package com.sma.admin.openapi;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.service.ApplicationService;
import com.sma.admin.openapi.internal.persistence.ApplicationMapper;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class OpenApiRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ApplicationService apps;
    @Autowired ApplicationMapper rows;
    @Test void createAndRotateReturnSecretsOnlyOnFirstExecutionAndKeepObjectAuthorization() {
        var actor=new SmaPrincipal(79501,"app-owner",79501,"admin",null,false,DataAccess.unrestricted(),Set.of("openapi:app:create","openapi:app:rotate","openapi:app:view"),"UTC");
        Users.as(actor,()->{
            String key=UUID.randomUUID().toString(), body="{\"name\":\"Governed app\",\"type\":1,\"enabled\":true,\"signatureRequired\":true}";
            var first=send(post("/api/openapi/apps").header("Idempotency-Key",key).contentType("application/json").content(body));success(first);
            long id=Long.parseLong(value(first,"/data/id"));String secret=value(first,"/data/appSecret");assertThat(secret).hasSize(43);
            var replay=send(post("/api/openapi/apps").header("Idempotency-Key",key).contentType("application/json").content(body));
            assertThat(replay.getResponse().getStatus()).isEqualTo(409);assertThat(text(replay)).doesNotContain(secret,"appSecret");
            assertThat(apps.page(new PageQuery(1,20,null,null),null,null,null).total()).isEqualTo(1);
            String rotateKey=UUID.randomUUID().toString();
            var rotated=send(post("/api/openapi/apps/"+id+"/rotate").header("Idempotency-Key",rotateKey));success(rotated);
            String next=value(rotated,"/data/appSecret");assertThat(next).hasSize(43).isNotEqualTo(secret);
            var repeated=send(post("/api/openapi/apps/"+id+"/rotate").header("Idempotency-Key",rotateKey));
            assertThat(repeated.getResponse().getStatus()).isEqualTo(409);assertThat(text(repeated)).doesNotContain(next,"appSecret");
            assertThat(rows.selectById(id).getCredentialVersion()).isEqualTo(1);
            Users.as(new SmaPrincipal(79502,"app-outsider",79501,"admin",null,false,DataAccess.own(),Set.of("openapi:app:rotate"),"UTC"),()->{
                var denied=send(post("/api/openapi/apps/"+id+"/rotate").header("Idempotency-Key",UUID.randomUUID().toString()));
                assertThat(value(denied,"/error")).isEqualTo("common.error.data-not-found");return null;
            });
            assertThat(rows.selectById(id).getCredentialVersion()).isEqualTo(1);
            assertThat(text(send(get("/api/openapi/apps/"+id)))).doesNotContain(next,secret,"appSecret","secretCipher");return null;
        });
    }
    private MvcResult send(MockHttpServletRequestBuilder request){var actor=CurrentUser.require();return Users.as(actor,()->{try{return mvc.perform(request.with(Users.request(actor))).andReturn();}catch(Exception ex){throw new IllegalStateException(ex);}});}
    private String text(MvcResult response){try{return response.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String value(MvcResult response,String pointer){return json.readTree(text(response)).at(pointer).asText();}
    private void success(MvcResult response){assertThat(response.getResponse().getStatus()).as(text(response)).isEqualTo(200);assertThat(value(response,"/code")).as(text(response)).isEqualTo("0");}
}
