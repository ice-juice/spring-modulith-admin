package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.persistence.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class StorageProfileTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-storage-profile-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired StorageProfileService service;@Autowired StorageProfileMapper profiles;@Autowired StorageRevisionMapper revisions;
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@MockitoSpyBean TenantSecretCodec secrets;@MockitoSpyBean FileClock clock;
    private final Instant now=Instant.parse("2026-11-12T00:00:00Z");
    @BeforeEach void setup(){doReturn(now).when(clock).now();}
    private SmaPrincipal actor(long tenant,String... permissions){return new SmaPrincipal(801,"storage-manager",tenant,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
    private <T>T as(long tenant,Supplier<T> work){return Users.as(actor(tenant,"file:storage:view","file:storage:manage"),work);}
    private StorageProfileService.Input cloud(String code,StorageProvider provider,String secret){
        String region=switch(provider){case S3->"us-east-1";case OSS->"cn-hangzhou";case R2->"auto";default->null;};
        return new StorageProfileService.Input(code,"Storage",provider,null,region,provider==StorageProvider.R2?"https://"+"a".repeat(32)+".r2.cloudflarestorage.com":null,"tenant-bucket","assets",
                secret==null?null:"TESTACCESS123456".toCharArray(),secret==null?null:secret.toCharArray(),null,null,Set.of("shop"),true,true);
    }
    private StorageRevisionEntity revision(long id,long version){return revisions.selectOne(new LambdaQueryWrapper<StorageRevisionEntity>().eq(StorageRevisionEntity::getProfileId,id).eq(StorageRevisionEntity::getConfigurationVersion,version));}
    private TenantConfigurationRef ref(long tenant,StorageProfileService.View view){return new TenantConfigurationRef(tenant,"file",view.id(),view.configurationVersion(),view.credentialVersion());}
    private String payload(long tenant,StorageProfileService.View view){try(var secret=secrets.decrypt(ref(tenant,view),"storage-credentials",revision(view.id(),view.configurationVersion()).getSecretEnvelope())){char[] value=secret.copy();try{return new String(value);}finally{Arrays.fill(value,'\0');}}}
    @Test void tenantCodesLocationsAndEncryptedCredentialsAreIndependent(){
        var a=as(1361,()->service.create(cloud("same",StorageProvider.S3,"tenant-a-secret")));var b=as(1362,()->service.create(cloud("same",StorageProvider.R2,"tenant-b-secret")));
        as(1361,()->{
            assertThat(service.get(a.id()).provider()).isEqualTo(StorageProvider.S3);assertThat(service.page(new PageQuery(1,20,null,null),null).total()).isEqualTo(1);
            for(Runnable denied:List.<Runnable>of(()->service.get(b.id()),()->service.stop(b.id(),1,StorageProfileService.Stop.ALL),()->service.update(b.id(),1,cloud("same",StorageProvider.R2,null))))
                assertThatThrownBy(denied::run).isInstanceOf(BizException.class);
            assertThat(revision(a.id(),1).getSecretEnvelope()).doesNotContain("tenant-a-secret","TESTACCESS123456");assertThat(payload(1361,a)).contains("tenant-a-secret");
            assertThatThrownBy(()->secrets.decrypt(ref(1362,b),"storage-credentials",revision(a.id(),1).getSecretEnvelope())).isInstanceOf(AccessDeniedException.class);return null;
        });
        as(1362,()->{assertThat(payload(1362,b)).contains("tenant-b-secret");return null;});
    }
    @Test void immutableRevisionsPreserveOrRotateCredentialsWithBoundReferences(){
        as(1363,()->{
            var first=service.create(cloud("versions",StorageProvider.OSS,"original-secret"));String envelope=revision(first.id(),1).getSecretEnvelope();
            var kept=service.update(first.id(),1,cloud("versions",StorageProvider.OSS,null));assertThat(kept.configurationVersion()).isEqualTo(2);assertThat(kept.credentialVersion()).isEqualTo(1);
            assertThat(revision(first.id(),1).getSecretEnvelope()).isEqualTo(envelope);assertThat(payload(1363,kept)).contains("original-secret");
            assertThatThrownBy(()->secrets.decrypt(ref(1363,kept),"storage-credentials",envelope)).isInstanceOf(BizException.class);
            var rotated=service.update(first.id(),2,cloud("versions",StorageProvider.OSS,"new-secret"));assertThat(rotated.credentialVersion()).isEqualTo(2);assertThat(payload(1363,rotated)).contains("new-secret");
            assertThat(payload(1363,first)).contains("original-secret");assertThat(revisions.selectCount(null)).isEqualTo(3);return null;
        });
    }
    @Test void emergencyFlagsWorkWithoutKeysAndOldEditsCannotReenable(){
        as(1364,()->{
            var first=service.create(cloud("stop",StorageProvider.S3,"protected-secret"));
            doThrow(new BizException(CommonErrorCode.SERVICE_UNAVAILABLE)).when(secrets).decrypt(any(),anyString(),anyString());
            var uploads=service.stop(first.id(),1,StorageProfileService.Stop.UPLOADS);assertThat(uploads.uploadsEnabled()).isFalse();assertThat(uploads.readsEnabled()).isTrue();
            var both=service.stop(first.id(),2,StorageProfileService.Stop.READS);assertThat(both.readsEnabled()).isFalse();assertThat(both.configurationVersion()).isEqualTo(1);
            assertThatThrownBy(()->service.update(first.id(),1,cloud("stop",StorageProvider.S3,null))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.STORAGE_VERSION_CONFLICT));
            assertThatThrownBy(()->service.update(first.id(),3,cloud("stop",StorageProvider.S3,null))).isInstanceOf(BizException.class);
            assertThat(service.get(first.id()).uploadsEnabled()).isFalse();assertThat(service.get(first.id()).version()).isEqualTo(3);return null;
        });
    }
    @Test void localProfilesDoNotNeedSecretsAndInvalidRequestsLeaveNoVersions(){
        as(1365,()->{
            var local=new StorageProfileService.Input("local","Local",StorageProvider.LOCAL,"default",null,null,null,"private",null,null,null,null,Set.of("shop"),true,true);
            doThrow(new BizException(CommonErrorCode.SERVICE_UNAVAILABLE)).when(secrets).encrypt(any(),anyString(),any());
            var created=service.create(local);assertThat(created.credentialsConfigured()).isFalse();assertThat(service.options().localRoots()).contains("default");
            assertThatThrownBy(()->service.create(local)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.STORAGE_PROFILE_CONFLICT));
            assertThatThrownBy(()->service.update(created.id(),1,cloud("local",StorageProvider.S3,null))).isInstanceOf(BizException.class);
            assertThatThrownBy(()->service.create(cloud("missing",StorageProvider.S3,null))).isInstanceOf(BizException.class);
            assertThat(profiles.selectCount(null)).isEqualTo(1);assertThat(revisions.selectCount(null)).isEqualTo(1);return null;
        });
    }
    @Test void temporaryCredentialExpiryIsRequiredAndCannotBeExtendedWithoutNewCredentials(){
        as(1366,()->{
            var base=cloud("temporary",StorageProvider.S3,"secret");Instant expiry=now.plusSeconds(3600).plusNanos(123456789);
            var missingExpiry=new StorageProfileService.Input("no-expiry",base.name(),base.provider(),null,base.region(),null,base.bucket(),base.prefix(),base.accessKeyId(),base.secretAccessKey(),"temporary-token".toCharArray(),null,base.applications(),true,true);
            assertThatThrownBy(()->service.create(missingExpiry)).isInstanceOf(BizException.class);
            var r2=cloud("r2-token",StorageProvider.R2,"secret");
            var unsupportedToken=new StorageProfileService.Input(r2.code(),r2.name(),r2.provider(),null,r2.region(),r2.endpoint(),r2.bucket(),r2.prefix(),r2.accessKeyId(),r2.secretAccessKey(),"temporary-token".toCharArray(),expiry,r2.applications(),true,true);
            assertThatThrownBy(()->service.create(unsupportedToken)).isInstanceOf(BizException.class);
            var input=new StorageProfileService.Input(base.code(),base.name(),base.provider(),null,base.region(),base.endpoint(),base.bucket(),base.prefix(),base.accessKeyId(),base.secretAccessKey(),"temporary-token".toCharArray(),expiry,base.applications(),true,true);
            var first=service.create(input);assertThat(first.credentialExpiresAt()).isEqualTo(expiry.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            var keep=new StorageProfileService.Input(base.code(),base.name(),base.provider(),null,base.region(),null,base.bucket(),base.prefix(),null,null,null,expiry,base.applications(),true,true);
            var kept=service.update(first.id(),1,keep);assertThat(payload(1366,kept)).contains("temporary-token");
            var extend=new StorageProfileService.Input(base.code(),base.name(),base.provider(),null,base.region(),null,base.bucket(),base.prefix(),null,null,null,expiry.plusSeconds(3600),base.applications(),true,true);
            assertThatThrownBy(()->service.update(first.id(),2,extend)).isInstanceOf(BizException.class);
            doReturn(now.plusSeconds(7200)).when(clock).now();assertThatThrownBy(()->service.update(first.id(),2,cloud(base.code(),StorageProvider.S3,null))).isInstanceOf(BizException.class);
            assertThat(service.stop(first.id(),2,StorageProfileService.Stop.ALL).readsEnabled()).isFalse();return null;
        });
    }
    @Test void httpPermissionsSecretRedactionAndWriteOnlyFingerprintAreEnforced()throws Exception{
        var manager=actor(1367,"file:storage:view","file:storage:manage");String key=UUID.randomUUID().toString();
        var body=json.createObjectNode().put("code","http").put("name","HTTP").put("provider","S3").put("region","us-east-1").put("bucket","tenant-bucket").put("prefix","assets")
                .put("accessKeyId","TESTACCESS123456").put("secretAccessKey","http-secret-one").put("uploadsEnabled",true).put("readsEnabled",true);body.putArray("applications").add("shop");
        var created=mvc.perform(post("/api/file/storage-profiles").with(Users.request(manager)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("http-secret-one","TESTACCESS123456","secretEnvelope","\"accessKeyId\":");String id=json.readTree(created).path("data").path("id").asString();assertThat(id).isNotBlank();
        body.put("secretAccessKey","http-secret-two");
        mvc.perform(post("/api/file/storage-profiles").with(Users.request(manager)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("common.request.conflict"));
        mvc.perform(get("/api/file/storage-profiles/"+id)).andExpect(status().isUnauthorized());
        var reader=actor(1367,"file:storage:view");mvc.perform(post("/api/file/storage-profiles/"+id+"/stop").with(Users.request(reader)).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"scope\":\"ALL\"}"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(()->Users.as(reader,()->service.stop(Long.parseLong(id),1,StorageProfileService.Stop.ALL))).isInstanceOf(AccessDeniedException.class);
        mvc.perform(get("/api/file/storage-profiles/"+id).with(Users.request(actor(1368,"file:storage:view")))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/api/file/storage-profiles/options").with(Users.request(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.data.providers.length()").value(4));
    }
}
