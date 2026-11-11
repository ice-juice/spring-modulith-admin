package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class IdentityEmailFoundationTests {
    private static Path keyFile;private static String firstKey;
    private static String randomKey(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getEncoder().encodeToString(value);}
    @DynamicPropertySource static void key(DynamicPropertyRegistry registry)throws Exception{
        firstKey=randomKey();keyFile=Files.createTempFile("sma-email-foundation-",".keys");Files.writeString(keyFile,"active=first\nkey.first="+firstKey);
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired IdentityEmailIndex index;@Autowired IdentityEmailRealmKeyMapper keys;@Autowired TransactionTemplate tx;
    @Autowired MailTemplateService templates;@Autowired MailProfileService profiles;@Autowired MailTemplateQuery published;
    @Autowired MockMvc mvc;@Autowired JsonMapper json;
    private SmaPrincipal manager(long tenant){return new SmaPrincipal(121,"email-manager",tenant,"admin",null,false,DataAccess.none(),Set.of(
            "identity:realm:manage","identity:client:manage","identity:client:view","mail:profile:manage","mail:template:manage","mail:template:publish","mail:template:view"),"UTC");}
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(manager(tenant),action);}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-email-foundation",action);}
    private IdentityRealmService.View realm(long tenant,String code){return admin(tenant,()->realms.create(new IdentityRealmService.Input(code,"Email Realm",true,true,true)));}
    private record Fixture(long tenant,IdentityClientService.View client,MailProfileService.View profile,Map<String,MailTemplateService.View> templates){}
    private Fixture fixture(long tenant){return admin(tenant,()->{
        var realm=realms.create(new IdentityRealmService.Input("email","Email Realm",true,true,true));
        var client=clients.create(new IdentityClientService.Input(realm.id(),"email-web","Email App","shop",IdentityClientService.Platform.WEB,true,false,null,null,
                Set.of("https://app.example.test/cb"),Set.of("https://app.example.test"),900,2592000));
        var profile=profiles.create(new MailProfileService.Input("email","SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test","smtp-test-password".toCharArray(),"sender@example.test","Sender",Set.of("identity"),100,10,true));
        var content=new MailTemplateContent("Verification","Code {{code}}, valid {{minutes}} minutes",false,
                Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,6,true),"minutes",new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER,2,true)),Set.of());
        Map<String,MailTemplateService.View> result=new HashMap<>();
        for(String scene:List.of("registration","password-reset","email-binding")){
            var draft=templates.create(new MailTemplateService.Input(scene,"identity",scene,"en-US",profile.code(),content));result.put(scene,templates.publish(draft.id(),draft.version()));
        }
        return new Fixture(tenant,client,profile,Map.copyOf(result));
    });}
    private IdentityEmailConfigurationService.Input input(boolean enabled){return new IdentityEmailConfigurationService.Input(enabled,"registration","password-reset","email-binding");}
    @Test void mailboxIndexIsStableAcrossEnvelopeRotationAndIndependentPerTenantAndRealm()throws Exception{
        var a=realm(1121,"email");var b=realm(1121,"other");var c=realm(1122,"email");String mailbox="A.B+tag@EXAMPLE.COM";
        String first=job(1121,()->index.index(a.id(),mailbox));assertThat(first).hasSize(64).doesNotContain("A.B");
        assertThat(job(1121,()->index.index(a.id(),"A.B+tag@example.com"))).isEqualTo(first);
        assertThat(job(1121,()->index.index(a.id(),"a.b+tag@example.com"))).isNotEqualTo(first);
        assertThat(job(1121,()->index.index(b.id(),mailbox))).isNotEqualTo(first);
        assertThat(job(1122,()->index.index(c.id(),mailbox))).isNotEqualTo(first);
        assertThatThrownBy(()->job(1122,()->index.index(a.id(),mailbox))).isInstanceOf(BizException.class);
        Path rotated=Files.createTempFile("sma-email-rotated-",".keys");
        try{
            Files.writeString(rotated,"active=second\nkey.first="+firstKey+"\nkey.second="+randomKey());
            var codec=new TenantCredentialCrypto(new TenantCredentialProperties(rotated.toString()));
            var next=new IdentityEmailIndex(realms,keys,codec);
            String afterRotation=job(1121,()->tx.execute(status->next.index(a.id(),mailbox)));assertThat(afterRotation).isEqualTo(first);
            job(1121,()->{
                var row=keys.selectOne(new LambdaQueryWrapper<IdentityEmailRealmKeyEntity>().eq(IdentityEmailRealmKeyEntity::getRealmId,a.id()));
                assertThat(row.getKeyEnvelope()).startsWith("v1.first.").doesNotContain(mailbox);
                row.setKeyEnvelope("invalid");keys.updateById(row);
                assertThatThrownBy(()->index.index(a.id(),mailbox)).isInstanceOf(BizException.class);
                assertThat(keys.selectCount(new LambdaQueryWrapper<IdentityEmailRealmKeyEntity>().eq(IdentityEmailRealmKeyEntity::getRealmId,a.id()))).isEqualTo(1);
                return null;
            });
        }finally{Files.deleteIfExists(rotated);}
    }
    @Test void sameTemplateCodesResolveOnlyWithinTenantAndStaleConfigurationCannotOverwrite(){
        var a=fixture(1123);var b=fixture(1124);
        var av=admin(a.tenant,()->configurations.put(a.client.id(),"en-US",0,input(true)));
        var bv=admin(b.tenant,()->configurations.put(b.client.id(),"en-US",0,input(true)));
        assertThat(av.id()).isNotEqualTo(bv.id());
        admin(a.tenant,()->{
            assertThat(configurations.list(a.client.id())).hasSize(1);
            assertThatThrownBy(()->configurations.list(b.client.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(()->configurations.put(b.client.id(),"en-US",0,input(true))).isInstanceOf(BizException.class);
            var current=configurations.put(a.client.id(),"en-US",av.version(),input(true));assertThat(current.version()).isEqualTo(2);
            assertThatThrownBy(()->configurations.put(a.client.id(),"en-US",av.version(),input(false))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.VERSION_CONFLICT));
            assertThat(published.available("identity","registration","registration","en-US",Map.of("code","000001","minutes","10")).orElseThrow().profileId()).isEqualTo(a.profile.id());
            return null;
        });
    }
    @Test void bindingRequiresPublishedCorrectPurposeAndCompatibleVariablesAndDisableWorksWithRevokedProfile(){
        var f=fixture(1125);admin(f.tenant,()->{
            assertThatThrownBy(()->configurations.put(f.client.id(),"zh-CN",0,input(true))).isInstanceOf(BizException.class);
            assertThatThrownBy(()->configurations.put(f.client.id(),"en-US",0,new IdentityEmailConfigurationService.Input(true,"password-reset","password-reset","email-binding"))).isInstanceOf(BizException.class);
            var created=configurations.put(f.client.id(),"en-US",0,input(true));
            var original=f.templates.get("registration");var bad=new MailTemplateContent("Bad code","{{code}} {{minutes}}",false,
                    Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER,6,true),"minutes",new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER,2,true)),Set.of());
            var edited=templates.update(original.id(),original.version(),new MailTemplateService.Input(original.code(),"identity","registration","en-US",f.profile.code(),bad));
            // Draft edits do not affect the published contract.
            assertThat(configurations.put(f.client.id(),"en-US",created.version(),input(true)).version()).isEqualTo(2);
            templates.publish(edited.id(),edited.version());
            assertThatThrownBy(()->configurations.put(f.client.id(),"en-US",2,input(true))).isInstanceOf(BizException.class);
            profiles.disable(f.profile.id(),f.profile.version());
            assertThat(configurations.put(f.client.id(),"en-US",2,input(false)).enabled()).isFalse();return null;
        });
    }
    @Test void realHttpRequiresClientManagementAndTemplateVisibilityAndNeverReturnsCredentials()throws Exception{
        var f=fixture(1126);String path="/api/identity/clients/"+f.client.id()+"/email-configurations";
        String body=json.writeValueAsString(Map.of("expectedVersion",0,"configuration",input(true)));
        var result=mvc.perform(put(path+"/en-US").header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(manager(f.tenant))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.version").value("1")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("smtp-test-password","keyEnvelope","secretEnvelope");
        var limited=new SmaPrincipal(122,"limited",f.tenant,"admin",null,false,DataAccess.none(),Set.of("identity:client:manage","identity:client:view"),"UTC");
        mvc.perform(put(path+"/en-US").header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(limited)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("expectedVersion",1,"configuration",input(true))))).andExpect(status().isForbidden());
        // Emergency disable does not require an available template or mail read permission.
        Thread.sleep(150);
        mvc.perform(put(path+"/en-US").header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(limited)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("expectedVersion",1,"configuration",input(false))))).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(get(path).with(Users.request(manager(1127)))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }
}
