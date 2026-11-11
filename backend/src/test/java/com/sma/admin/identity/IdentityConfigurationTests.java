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
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class IdentityConfigurationTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-p09-identity-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;
    @Autowired IdentityClientRevisionMapper revisions;@MockitoSpyBean TenantSecretCodec secrets;
    @Autowired MockMvc mvc;@Autowired JsonMapper json;
    private SmaPrincipal admin(long tenant,String...permissions){return new SmaPrincipal(801,"identity-admin",tenant,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
    private SmaPrincipal manager(long tenant){return admin(tenant,"identity:realm:view","identity:realm:manage","identity:client:view","identity:client:manage");}
    private <T>T as(long tenant,Supplier<T> action){return Users.as(manager(tenant),action);}
    private IdentityRealmService.View realm(long tenant){return as(tenant,()->realms.create(new IdentityRealmService.Input("shop","Shop",true,true,true)));}
    private IdentityClientService.Input input(long realmId,char[] secret){return new IdentityClientService.Input(realmId,"shop-web","Shop Web","shop",IdentityClientService.Platform.WEB,
            true,true,"123456-example.apps.googleusercontent.com",secret,Set.of("https://app.example.test/oauth/callback"),Set.of("https://app.example.test"),900,2592000);}
    private IdentityClientRevisionEntity revision(long client,long version){return revisions.selectOne(new LambdaQueryWrapper<IdentityClientRevisionEntity>()
            .eq(IdentityClientRevisionEntity::getClientId,client).eq(IdentityClientRevisionEntity::getConfigurationVersion,version));}
    private TenantConfigurationRef ref(long tenant,IdentityClientService.View c){return new TenantConfigurationRef(tenant,"identity",c.id(),c.configurationVersion(),c.credentialVersion());}

    @Test void duplicateCodesAcrossTenantsHaveIndependentGoogleSecretsAndOpaqueHandles(){
        var ra=realm(971);var rb=realm(972);
        var a=as(971,()->clients.create(input(ra.id(),"tenant-a-google-secret".toCharArray())));
        var b=as(972,()->clients.create(input(rb.id(),"tenant-b-google-secret".toCharArray())));
        assertThat(a.loginHandle()).hasSize(43).isNotEqualTo(b.loginHandle());
        as(971,()->{
            assertThat(clients.page(new PageQuery(1,20,null,null),null).total()).isEqualTo(1);
            assertThatThrownBy(()->clients.get(b.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(()->realms.get(rb.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(()->clients.create(input(rb.id(),"other-google-secret".toCharArray()))).isInstanceOf(BizException.class);
            var row=revision(a.id(),1);assertThat(row.getSecretEnvelope()).doesNotContain("tenant-a-google-secret");
            try(var value=secrets.decrypt(ref(971,a),"google-client-secret",row.getSecretEnvelope())){assertThat(value.copy()).containsExactly("tenant-a-google-secret".toCharArray());}
            assertThatThrownBy(()->secrets.decrypt(ref(972,b),"google-client-secret",row.getSecretEnvelope())).isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }
    @Test void revisionsAreImmutableBlankSecretRetainsItAndStaleWritesCannotOverwrite(){
        var r=realm(973);as(973,()->{
            var a=clients.create(input(r.id(),"original-google-secret".toCharArray()));String original=revision(a.id(),1).getSecretEnvelope();
            var next=clients.update(a.id(),1,input(r.id(),null));
            assertThat(next.configurationVersion()).isEqualTo(2);assertThat(next.credentialVersion()).isEqualTo(1);assertThat(next.securityVersion()).isEqualTo(2);
            assertThat(revision(a.id(),1).getSecretEnvelope()).isEqualTo(original);
            try(var value=secrets.decrypt(ref(973,next),"google-client-secret",revision(a.id(),2).getSecretEnvelope())){assertThat(value.copy()).containsExactly("original-google-secret".toCharArray());}
            assertThatThrownBy(()->clients.update(a.id(),1,input(r.id(),"stale-google-secret".toCharArray()))).isInstanceOfSatisfying(BizException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.VERSION_CONFLICT));
            var rotated=clients.update(a.id(),2,input(r.id(),"rotated-google-secret".toCharArray()));assertThat(rotated.credentialVersion()).isEqualTo(2);
            assertThat(rotated.loginHandle()).isEqualTo(a.loginHandle());return null;
        });
    }
    @Test void emergencyDisableWorksWithoutSecretsAndAdvancesRealmAndClientSecurityGenerations(){
        var r=realm(974);as(974,()->{
            var a=clients.create(input(r.id(),"disable-google-secret".toCharArray()));
            org.mockito.Mockito.doThrow(new BizException(CommonErrorCode.SERVICE_UNAVAILABLE)).when(secrets).decrypt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
            var disabled=clients.disable(a.id(),1);assertThat(disabled.enabled()).isFalse();assertThat(disabled.securityVersion()).isEqualTo(2);
            assertThat(disabled.configurationVersion()).isEqualTo(1);
            assertThatThrownBy(()->clients.update(a.id(),1,input(r.id(),null))).isInstanceOfSatisfying(BizException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.VERSION_CONFLICT));
            var realmDisabled=realms.disable(r.id(),1);assertThat(realmDisabled.securityVersion()).isEqualTo(2);assertThat(realmDisabled.enabled()).isFalse();
            assertThatThrownBy(()->clients.update(a.id(),2,input(r.id(),null))).isInstanceOf(BizException.class);return null;
        });
    }
    @Test void nativeClientsDoNotNeedSecretsAndWebGoogleCannotEnableWithoutOne(){
        var r=realm(975);as(975,()->{
            assertThatThrownBy(()->clients.create(input(r.id(),null))).isInstanceOf(BizException.class);
            var nativeInput=new IdentityClientService.Input(r.id(),"shop-ios","Shop iOS","shop",IdentityClientService.Platform.IOS,true,true,
                    "123456-native.apps.googleusercontent.com",null,Set.of("com.example.shop:/oauth/callback"),Set.of(),900,2592000);
            var nativeClient=clients.create(nativeInput);assertThat(nativeClient.secretConfigured()).isFalse();
            assertThat(revision(nativeClient.id(),1).getSecretEnvelope()).isNull();
            assertThatThrownBy(()->clients.create(nativeInput)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.DUPLICATE_CODE));
            assertThat(revisions.selectCount(null)).isEqualTo(1);return null;
        });
    }
    @Test void immutableRealmApplicationAndGoogleClientChangesCannotReuseUnrelatedSecret(){
        var r=realm(976);as(976,()->{
            var a=clients.create(input(r.id(),"owned-google-secret".toCharArray()));
            var old=input(r.id(),null);
            var changed=new IdentityClientService.Input(r.id(),old.code(),old.name(),old.application(),old.platform(),true,true,"987654-other.apps.googleusercontent.com",
                    null,old.callbackUris(),old.allowedOrigins(),old.accessTokenSeconds(),old.refreshTokenSeconds());
            assertThatThrownBy(()->clients.update(a.id(),1,changed)).isInstanceOf(BizException.class);
            var otherRealm=realms.create(new IdentityRealmService.Input("other","Other",true,true,true));
            assertThatThrownBy(()->clients.update(a.id(),1,input(otherRealm.id(),"changed-google-secret".toCharArray()))).isInstanceOf(BizException.class);
            assertThat(clients.get(a.id()).configurationVersion()).isEqualTo(1);return null;
        });
    }
    @Test void cookiePolicyAndExactGooglePresenterAreVersionedAndPreservedWhenOmitted(){
        var r=realm(979);as(979,()->{
            var old=input(r.id(),"cookie-google-secret".toCharArray());
            String presenter="123456-android.apps.googleusercontent.com";
            var configured=new IdentityClientService.Input(r.id(),old.code(),old.name(),old.application(),old.platform(),true,true,old.googleClientId(),old.googleSecret(),
                    old.callbackUris(),old.allowedOrigins(),900,2592000,IdentityClientService.CookieSameSite.NONE,presenter);
            var first=clients.create(configured);
            var next=clients.update(first.id(),first.version(),input(r.id(),null));
            assertThat(next.cookieSameSite()).isEqualTo(IdentityClientService.CookieSameSite.NONE);
            assertThat(next.googleAuthorizedParty()).isEqualTo(presenter);
            assertThat(revision(first.id(),1).getCookieSameSite()).isEqualTo("NONE");
            assertThat(revision(first.id(),2).getGoogleAuthorizedParty()).isEqualTo(presenter);
            var reset=new IdentityClientService.Input(r.id(),old.code(),old.name(),old.application(),old.platform(),true,true,old.googleClientId(),null,
                    old.callbackUris(),old.allowedOrigins(),900,2592000,IdentityClientService.CookieSameSite.LAX,"");
            var third=clients.update(first.id(),next.version(),reset);
            assertThat(third.cookieSameSite()).isEqualTo(IdentityClientService.CookieSameSite.LAX);
            assertThat(third.googleAuthorizedParty()).isNull();
            assertThat(revision(first.id(),1).getGoogleAuthorizedParty()).isEqualTo(presenter);
            return null;
        });
    }
    @Test void realManagementHttpEnforcesPermissionTenantAndWriteOnlyGoogleSecret()throws Exception{
        var r=realm(977);var configuration=json.createObjectNode().put("realmId",r.id().toString()).put("code","http-web").put("name","HTTP Web").put("application","shop")
                .put("platform","WEB").put("enabled",true).put("googleEnabled",true).put("googleClientId","123456-http.apps.googleusercontent.com")
                .put("googleSecret","http-google-secret-value").put("accessTokenSeconds",900).put("refreshTokenSeconds",2592000);
        configuration.putArray("callbackUris").add("https://app.example.test/oauth/callback");configuration.putArray("allowedOrigins").add("https://app.example.test");
        String createKey=UUID.randomUUID().toString();
        String created=mvc.perform(post("/api/identity/clients").header("Idempotency-Key",createKey).with(Users.request(manager(977))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(configuration)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.secretConfigured").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("http-google-secret-value","secretEnvelope","googleSecret");String id=json.readTree(created).path("data").path("id").asString();
        mvc.perform(post("/api/identity/clients").header("Idempotency-Key",createKey).with(Users.request(manager(977))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(configuration)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("common.request.completed"));
        configuration.put("googleSecret","different-http-google-secret");
        mvc.perform(post("/api/identity/clients").header("Idempotency-Key",createKey).with(Users.request(manager(977))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(configuration)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("common.request.conflict"));
        mvc.perform(get("/api/identity/clients/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/identity/clients/"+id).with(Users.request(manager(978)))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
        var reader=admin(977,"identity:client:view");
        mvc.perform(post("/api/identity/clients/"+id+"/disable").with(Users.request(reader)).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        Users.as(reader,()->{assertThatThrownBy(()->clients.disable(Long.parseLong(id),1)).isInstanceOf(AccessDeniedException.class);return null;});
        configuration.remove("googleSecret");var update=json.createObjectNode().put("expectedVersion",1);update.set("configuration",configuration);
        mvc.perform(put("/api/identity/clients/"+id).header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(manager(977))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configurationVersion").value("2")).andExpect(jsonPath("$.data.secretConfigured").value(true));
    }
}
