package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import jakarta.servlet.http.Cookie;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class UploadTaskHttpTests {
    private static Path keyFile,root;
    private static final String ORIGIN="https://app.example.test",ADMIN="/api/file/upload-tasks",APP="/app/file/upload-tasks";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-upload-http-",".keys");root=Files.createTempDirectory("sma-upload-http-files-");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.file.storage.local-roots.default",()->root.toString());registry.add("sma.file.tasks.part-bytes",()->1048576);
        registry.add("sma.file.local-transfer.minimum-free-bytes",()->0);
    }
    @AfterAll static void cleanup()throws Exception{
        if(keyFile!=null)Files.deleteIfExists(keyFile);
        if(root!=null)try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired StorageProfileService profiles;@Autowired StorageSceneService scenes;
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityAuthority authority;@Autowired IdentitySessionStore sessions;
    @MockitoSpyBean BuiltinFileScenes builtin;
    private SmaPrincipal actor(long tenant,long user,String portal){return new SmaPrincipal(user,"upload-http",tenant,portal,null,false,DataAccess.none(),Set.of(
            "file:storage:manage","file:scene:manage","file:scene:publish","file:file:upload","identity:realm:manage","identity:client:manage"),"UTC");}
    private <T>T admin(long tenant,Supplier<T> work){return Users.as(actor(tenant,904,"admin"),work);}
    private void fixture(long tenant){admin(tenant,()->{
        var profile=profiles.create(new StorageProfileService.Input("local","Local",StorageProvider.LOCAL,"default",null,null,null,"assets",null,null,null,null,Set.of("file"),true,true));
        var scene=scenes.create(new StorageSceneService.Input("file.attachment","Files",new StorageSceneService.Configuration(Set.of(profile.id()),profile.id(),Set.of(FileFormat.TEXT),Set.of(UploadMode.SERVER,UploadMode.MULTIPART),5L*1048576,30)));
        scenes.publish(scene.id(),1);return null;
    });}
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private String body(byte[] bytes)throws Exception{return json.writeValueAsString(Map.of("application","file","scene","file.attachment","mode","MULTIPART","name","notes.txt","format","TEXT","size",bytes.length,"checksum",hash(bytes)));}
    private JsonNode ok(MockHttpServletRequestBuilder request)throws Exception{
        var result=mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
    private MockHttpServletRequestBuilder asAdmin(long tenant,MockHttpServletRequestBuilder request){return request.with(Users.request(actor(tenant,904,"admin")));}
    private JsonNode initiateAdmin(long tenant,byte[] bytes,String key)throws Exception{return ok(asAdmin(tenant,post(ADMIN)).contentType("application/json").header("Idempotency-Key",key).content(body(bytes)));}
    private MockHttpServletRequestBuilder part(String path,byte[] bytes)throws Exception{return put(path).contentType("application/octet-stream").header("X-Upload-Size",bytes.length).header("X-Upload-SHA256",hash(bytes)).content(bytes);}
    @Test void adminUploadsActualPartsResumesAndCompletesOnlyIntoQuarantine()throws Exception{
        long tenant=1441;fixture(tenant);byte[] bytes=new byte[1048576+17];new Random(7).nextBytes(bytes);String key=UUID.randomUUID().toString();var task=initiateAdmin(tenant,bytes,key);
        assertThat(task.path("fileId").isString()).isTrue();String id=task.path("uploadId").asString();
        Thread.sleep(150);assertThat(initiateAdmin(tenant,bytes,key).path("uploadId").asString()).isEqualTo(id);
        byte[] first=Arrays.copyOf(bytes,1048576),last=Arrays.copyOfRange(bytes,1048576,bytes.length);
        ok(asAdmin(tenant,part(ADMIN+"/"+id+"/parts/2",last)));ok(asAdmin(tenant,part(ADMIN+"/"+id+"/parts/1",first)));
        Thread.sleep(150);var replay=ok(asAdmin(tenant,part(ADMIN+"/"+id+"/parts/1",first)));assertThat(replay.path("receivedParts").asInt()).isEqualTo(2);
        assertThat(ok(asAdmin(tenant,get(ADMIN+"/"+id+"/parts")).queryParam("limit","1")).size()).isEqualTo(1);
        var completed=ok(asAdmin(tenant,post(ADMIN+"/"+id+"/complete")).contentType("application/json").content("{\"version\":\""+replay.path("version").asString()+"\"}"));
        assertThat(completed.path("state").asString()).isEqualTo("SCANNING");
        assertThat(ok(asAdmin(tenant,get(ADMIN+"/"+id))).path("state").asString()).isEqualTo("SCANNING");
        try(var paths=Files.walk(root.resolve("tenants/"+tenant))){var frozen=paths.filter(p->p.getFileName().toString().startsWith("frozen-")).toList();assertThat(frozen).hasSize(1);assertThat(Files.readAllBytes(frozen.getFirst())).containsExactly(bytes);}
    }
    @Test void adminCannotUseForeignTenantOwnerPortalOrUnpermittedScene()throws Exception{
        long tenant=1442;fixture(tenant);fixture(1443);byte[] bytes="owned".getBytes();String id=initiateAdmin(tenant,bytes,UUID.randomUUID().toString()).path("uploadId").asString();
        for(var other:List.of(actor(tenant,905,"admin"),actor(tenant,904,"other"),actor(1443,904,"admin"))){
            mvc.perform(get(ADMIN+"/"+id).with(Users.request(other))).andExpect(status().isOk()).andExpect(jsonPath("$.error").value("file.error.upload-not-found"));
        }
        mvc.perform(post(ADMIN).with(Users.request(Users.principal(906,tenant,DataAccess.none()))).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes)))
                .andExpect(status().isForbidden());
    }
    private record Client(long tenant,IdentityClientService.View client,IdentitySessionStore.Issued session){}
    private Client client(long tenant,boolean web,String application,Long realm,String subject){return admin(tenant,()->{
        long realmId=realm==null?realms.create(new IdentityRealmService.Input("r"+UUID.randomUUID().toString().substring(0,8),"Upload",true,true,true)).id():realm;
        var client=clients.create(new IdentityClientService.Input(realmId,"c"+UUID.randomUUID().toString().substring(0,8),"Upload",application,
                web?IdentityClientService.Platform.WEB:IdentityClientService.Platform.ANDROID,true,true,"123456-upload.apps.googleusercontent.com",web?"upload-http-secret".toCharArray():null,
                Set.of(ORIGIN+"/cb"),web?Set.of(ORIGIN):Set.of(),900,2592000));
        // This fixture only prepares a real database session. Google proof validation has its own protocol tests.
        var issued=ActorContext.withinJob(tenant,"file-http-session-fixture",()->sessions.google(authority.capture(client.loginHandle()),
                new GoogleIdTokenVerifier.Verified("https://accounts.google.com",subject,null,false)));
        return new Client(tenant,client,issued);
    });}
    private MockHttpServletRequestBuilder app(Client c,MockHttpServletRequestBuilder request){
        request.queryParam("appClient",c.client.loginHandle()).header("Host","t"+c.tenant+"-admin.test").secure(true).with(r->{r.setScheme("https");r.setServerPort(443);return r;});
        if(c.client.platform()==IdentityClientService.Platform.WEB)request.header("Origin",ORIGIN).header("X-App-CSRF",c.session.csrfToken())
            .cookie(new Cookie("__Host-sma-access-"+c.client.loginHandle(),c.session.accessToken()),new Cookie("__Host-sma-csrf-"+c.client.loginHandle(),c.session.csrfToken()));
        else request.header("Authorization","Bearer "+c.session.accessToken());return request;
    }
    private void permitTestApplication(){
        // Test-only stand-in for a registered business FileScenePolicy. Production builtin scenes still reject APP_USER.
        doAnswer(invocation->{ActorRef actor=invocation.getArgument(0);return actor.kind()==ActorRef.Kind.APP_USER || (boolean)invocation.callRealMethod();}).when(builtin).canUse(any(),any());
    }
    @Test void nativeRealSessionsEnforceSceneAudienceAndTaskClientEvenForSameSubject()throws Exception{
        long tenant=1444;fixture(tenant);var c=client(tenant,false,"file",null,"owner");byte[] bytes="native bytes".getBytes();
        mvc.perform(app(c,post(APP)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes))).andExpect(status().isForbidden());
        permitTestApplication();var task=ok(app(c,post(APP)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes)));String id=task.path("uploadId").asString();
        var stored=ok(app(c,part(APP+"/"+id+"/parts/1",bytes)));assertThat(stored.path("receivedBytes").asLong()).isEqualTo(bytes.length);
        var another=client(tenant,false,"file",c.client.realmId(),"owner");assertThat(another.session.subjectId()).isEqualTo(c.session.subjectId());
        mvc.perform(app(another,get(APP+"/"+id))).andExpect(status().isOk()).andExpect(jsonPath("$.error").value("file.error.upload-not-found"));
        var wrongAudience=client(tenant,false,"shop",c.client.realmId(),"owner");
        mvc.perform(app(wrongAudience,post(APP)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes))).andExpect(status().isForbidden());
        mvc.perform(app(another,get(APP+"/"+id)).with(r->{r.removeHeader("Authorization");r.addHeader("Authorization","Bearer "+c.session.accessToken());return r;})).andExpect(status().isUnauthorized());
        mvc.perform(app(c,get(ADMIN+"/"+id))).andExpect(status().isUnauthorized());
        assertThat(ok(app(c,post(APP+"/"+id+"/complete")).contentType("application/json").content("{\"version\":\""+stored.path("version").asString()+"\"}")).path("state").asString()).isEqualTo("SCANNING");
    }
    @Test void webUploadKeepsCsrfAndExactCorsAndDoesNotAdmitAnonymousBodies()throws Exception{
        long tenant=1445;fixture(tenant);permitTestApplication();var c=client(tenant,true,"file",null,"web-owner");byte[] bytes="web bytes".getBytes();
        var task=ok(app(c,post(APP)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes)));String id=task.path("uploadId").asString();
        mvc.perform(app(c,part(APP+"/"+id+"/parts/1",bytes)).with(r->{r.removeHeader("X-App-CSRF");return r;})).andExpect(status().isForbidden());
        var stored=ok(app(c,part(APP+"/"+id+"/parts/1",bytes)));assertThat(stored.path("receivedParts").asInt()).isEqualTo(1);
        mvc.perform(app(c,post(APP)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(body(bytes)).with(r->{r.setCookies();r.removeHeader("X-App-CSRF");return r;}))
                .andExpect(status().isForbidden());
        mvc.perform(app(c,options(APP+"/"+id+"/parts/1")).header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","content-type,x-app-csrf,x-upload-size,x-upload-sha256"))
                .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN));
        mvc.perform(app(c,options(APP)).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","x-tenant-id")).andExpect(status().isForbidden());
    }
    @Test void invalidHttpBodiesAreRejectedAndCancellationDoesNotPretendCleanup()throws Exception{
        long tenant=1446;fixture(tenant);byte[] bytes="cancel me".getBytes();var task=initiateAdmin(tenant,bytes,UUID.randomUUID().toString());String id=task.path("uploadId").asString();
        mvc.perform(asAdmin(tenant,post(ADMIN)).contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString()).content(" ".repeat(65537))).andExpect(status().isPayloadTooLarge());
        mvc.perform(asAdmin(tenant,part(ADMIN+"/"+id+"/parts/1",bytes)).header("X-Upload-Size",bytes.length)).andExpect(status().isBadRequest());
        var aborted=ok(asAdmin(tenant,delete(ADMIN+"/"+id)).contentType("application/json").content("{\"version\":\""+task.path("version").asString()+"\"}"));assertThat(aborted.path("state").asString()).isEqualTo("ABORTING");
        mvc.perform(asAdmin(tenant,part(ADMIN+"/"+id+"/parts/1",bytes))).andExpect(status().isOk()).andExpect(jsonPath("$.error").value("file.error.upload-state"));
        assertThat(Files.exists(root.resolve("tenants/"+tenant))).isFalse();
    }
}
