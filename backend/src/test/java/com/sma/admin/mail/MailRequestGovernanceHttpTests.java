package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc @Import(MailOutboxTests.Policy.class)
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class MailRequestGovernanceHttpTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-security-mail-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired MailProfileService profiles;
    @Autowired MailTemplateService templates;@Autowired MailSendApi sending;
    @Test void templatePublicationIsLocalPreviewIsQueryAndTaskCancellationUsesItsNativeState()throws Exception{
        var actor=new SmaPrincipal(101,"manager",79921,"admin",null,false,DataAccess.unrestricted(),Set.of(
                "mail:profile:manage","mail:template:manage","mail:template:publish","mail:template:view","mail:delivery:manage","mail:delivery:view"),"UTC");
        var template=Users.as(actor,()->{
            var profile=profiles.create(new MailProfileService.Input("governed","SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                    "mailer@example.test","test-password".toCharArray(),"sender@example.test","Sender",Set.of("shop"),100,10,true));
            var content=new MailTemplateContent("Notice","Code {{code}}",false,Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,20,true)),Set.of());
            return templates.create(new MailTemplateService.Input("notice","shop","registration","zh-CN",profile.code(),content));
        });
        String key=UUID.randomUUID().toString(),publishPath="/api/mail/templates/"+template.id()+"/publish";
        var first=mvc.perform(post(publishPath).header("Idempotency-Key",key).contentType("application/json").content("{\"expectedVersion\":1}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);assertThat(first.getHeader("X-Operation-Id")).isNotBlank();
        assertThat(json.readTree(first.getContentAsString()).at("/code").asInt()).isZero();
        var replay=mvc.perform(post(publishPath).header("Idempotency-Key",key).contentType("application/json").content("{\"expectedVersion\":1}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(replay.getStatus()).isEqualTo(409);
        var preview=mvc.perform(post("/api/mail/templates/"+template.id()+"/preview").contentType("application/json").content("{\"code\":\"123456\"}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(preview.getStatus()).as(preview.getContentAsString()).isEqualTo(200);assertThat(preview.getHeader("X-Request-Policy")).isEqualTo("QUERY");
        var delivery=Users.as(actor,()->sending.enqueue(new MailSendApi.Command("shop","registration",template.code(),"zh-CN","recipient@example.test",Map.of("code","123456"),Instant.now().plusSeconds(600),UUID.randomUUID().toString())));
        var cancelled=mvc.perform(post("/api/mail/deliveries/"+delivery.id()+"/cancel").with(Users.request(actor))).andReturn().getResponse();
        assertThat(cancelled.getStatus()).as(cancelled.getContentAsString()).isEqualTo(200);assertThat(cancelled.getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(cancelled.getHeader("X-Operation-Id")).isNull();assertThat(json.readTree(cancelled.getContentAsString()).at("/data/state").asText()).isEqualTo("CANCELLED");
        Thread.sleep(150);
        var again=mvc.perform(post("/api/mail/deliveries/"+delivery.id()+"/cancel").with(Users.request(actor))).andReturn().getResponse();
        assertThat(json.readTree(again.getContentAsString()).at("/data/state").asText()).isEqualTo("CANCELLED");
        var foreign=new SmaPrincipal(101,"manager",79922,"admin",null,false,DataAccess.unrestricted(),actor.permissions(),"UTC");
        var denied=mvc.perform(post("/api/mail/deliveries/"+delivery.id()+"/cancel").with(Users.request(foreign))).andReturn().getResponse();
        assertThat(json.readTree(denied.getContentAsString()).at("/code").asInt()).isEqualTo(404);
        assertThat(cancelled.getContentAsString()).doesNotContain("123456","test-password","recipient@example.test");
    }
}
