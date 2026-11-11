package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sma.admin.identity.EmailIdentityTestSupport.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.Cookie;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryMapper;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
class IdentityEmailApplicationHttpTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-email-http-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired MockMvc mvc;@Autowired JsonMapper json;
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired MailOutboxService outbox;
    @Autowired IdentityEmailChallengeMapper challengeRows;@Autowired MailDeliveryMapper mailRows;@Autowired IdentityEmailCredentialMapper credentials;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;
    private Instant now;private final String password="Q7d4e10594",replacement="K7n905d156";
    @BeforeEach void setup(){now=Instant.parse("2026-11-12T00:00:00Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();}
    private Fixture fixture(long tenant,boolean web){
        var base=EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);if(web)return base;
        return admin(tenant,()->{
            var client=clients.create(new IdentityClientService.Input(base.realm().id(),"native","Native","shop",IdentityClientService.Platform.ANDROID,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(),900,2592000));
            configurations.put(client.id(),"en-US",0,new IdentityEmailConfigurationService.Input(true,"registration","password-reset","email-binding"));return new Fixture(tenant,base.realm(),client);
        });
    }
    private boolean web(Fixture f){return f.client().platform()==IdentityClientService.Platform.WEB;}
    private String cookie(Fixture f,String kind){return "__Host-sma-"+kind+"-"+f.client().loginHandle();}
    private static class Jar {
        final Map<String,Cookie> values=new HashMap<>();
        void accept(MvcResult result){for(String header:result.getResponse().getHeaders("Set-Cookie")){
            var pair=header.split(";",2)[0].split("=",2);if(header.contains("Max-Age=0") || pair.length<2 || pair[1].isEmpty())values.remove(pair[0]);else values.put(pair[0],new Cookie(pair[0],pair[1]));}}
        Cookie[] all(){return values.values().toArray(Cookie[]::new);}
    }
    private MockHttpServletRequestBuilder request(Fixture f,MockHttpServletRequestBuilder builder){
        builder.queryParam("appClient",f.client().loginHandle()).header("Host","t"+f.tenant()+"-admin.test").secure(true).with(r->{r.setScheme("https");r.setServerPort(443);return r;});
        if(web(f))builder.header("Origin",ORIGIN);return builder;
    }
    private MockHttpServletRequestBuilder postRequest(Fixture f,Jar jar,String path,Map<String,?> body){
        var request=request(f,post("/app/identity"+path)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if(!jar.values.isEmpty())request.cookie(jar.all());if(web(f) && jar.values.containsKey(cookie(f,"csrf")))request.header("X-App-CSRF",jar.values.get(cookie(f,"csrf")).getValue());return request;
    }
    private JsonNode data(MvcResult result){return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
    private JsonNode success(Fixture f,Jar jar,String path,Map<String,?> body)throws Exception{
        var response=mvc.perform(postRequest(f,jar,path,body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(response);
        if(!web(f))assertThat(response.getResponse().getHeaders("Set-Cookie")).isEmpty();return data(response);
    }
    private Jar bootstrap(Fixture f)throws Exception{
        var jar=new Jar();var response=mvc.perform(request(f,get("/app/identity/bootstrap"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(response);return jar;
    }
    private String code(Fixture f,String handle){return job(f.tenant(),()->{
        String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(handle.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        var row=challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getHandleHash,hash));
        String body=outbox.payload(mailRows.selectById(row.getDeliveryId())).body();var match=java.util.regex.Pattern.compile("Code ([0-9]{6}),").matcher(body);assertThat(match.find()).isTrue();return match.group(1);
    });}
    private JsonNode challenge(Fixture f,Jar jar,String purpose,String email)throws Exception{return success(f,jar,"/email/challenges",Map.of("purpose",purpose,"email",email,"language","en-US"));}
    private Map<String,Object> verification(Fixture f,JsonNode started,String purpose,String code){
        var body=new HashMap<String,Object>();body.put("purpose",purpose);body.put("handle",started.path("handle").asString());body.put("code",code);
        if(!web(f))body.put("proof",started.path("proof").asString());return body;
    }
    private Map<String,Object> credential(Fixture f,JsonNode started,JsonNode grant,String password){
        var body=new HashMap<String,Object>();body.put("grant",grant.path("grant").asString());body.put("password",password);if(!web(f))body.put("proof",started.path("proof").asString());return body;
    }
    private JsonNode verify(Fixture f,Jar jar,JsonNode started,String purpose)throws Exception{return success(f,jar,"/email/verifications",verification(f,started,purpose,code(f,started.path("handle").asString())));}
    private JsonNode register(Fixture f,Jar jar,String email)throws Exception{
        var started=challenge(f,jar,"REGISTRATION",email);var grant=verify(f,jar,started,"REGISTRATION");return success(f,jar,"/registrations",credential(f,started,grant,password));
    }
    @Test void h5RegistrationPasswordLoginResetAndRevocationUseSecurePurposeCookies()throws Exception{
        var f=fixture(1291,true);var jar=bootstrap(f);var started=challenge(f,jar,"REGISTRATION","owner@example.test");assertThat(started.has("proof")).isFalse();
        assertThat(jar.values).containsKey(cookie(f,"registration-proof"));var grant=verify(f,jar,started,"REGISTRATION");
        var registered=success(f,jar,"/registrations",credential(f,started,grant,password));assertThat(registered.has("accessToken")).isFalse();assertThat(registered.has("refreshToken")).isFalse();
        assertThat(jar.values).doesNotContainKey(cookie(f,"registration-proof"));Cookie[] original=jar.all();
        var loggedIn=success(f,jar,"/password/sessions",Map.of("email","owner@example.test","password",password));assertThat(loggedIn.path("subjectId")).isEqualTo(registered.path("subjectId"));
        Cookie[] other=jar.all();now=now.plusSeconds(61);var reset=challenge(f,jar,"PASSWORD_RESET","owner@example.test");var verified=verify(f,jar,reset,"PASSWORD_RESET");
        success(f,jar,"/password/resets",credential(f,reset,verified,replacement));assertThat(jar.values).isEmpty();
        for(var cookies:List.of(original,other))mvc.perform(request(f,get("/app/identity/sessions/current")).cookie(cookies)).andExpect(status().isUnauthorized());
        jar=bootstrap(f);mvc.perform(postRequest(f,jar,"/password/sessions",Map.of("email","owner@example.test","password",password))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var newLogin=success(f,jar,"/password/sessions",Map.of("email","owner@example.test","password",replacement));assertThat(newLogin.path("subjectId")).isEqualTo(registered.path("subjectId"));
    }
    @Test void nativeRegistrationRequiresJsonProofAndProducesBearerSessionsWithoutCookies()throws Exception{
        var f=fixture(1292,false);var jar=bootstrap(f);var started=challenge(f,jar,"REGISTRATION","owner@example.test");assertThat(started.path("proof").asString()).hasSize(43);
        var missing=verification(f,started,"REGISTRATION",code(f,started.path("handle").asString()));missing.remove("proof");
        mvc.perform(postRequest(f,jar,"/email/verifications",missing)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var grant=verify(f,jar,started,"REGISTRATION");var registered=success(f,jar,"/registrations",credential(f,started,grant,password));String access=registered.path("accessToken").asString();assertThat(access).hasSize(43);
        mvc.perform(request(f,get("/app/identity/sessions/current")).header("Authorization","Bearer "+access)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(postRequest(f,jar,"/password/sessions",Map.of("email","owner@example.test","password",password)).header("Authorization","Bearer "+access)).andExpect(status().isBadRequest());
        success(f,jar,"/password/sessions",Map.of("email","owner@example.test","password",password));
    }
    @Test void webProofBodyCsrfMissingOriginAndNativeProofCookiesAreRejected()throws Exception{
        var f=fixture(1293,true);var jar=bootstrap(f);var started=challenge(f,jar,"REGISTRATION","owner@example.test");
        var body=verification(f,started,"REGISTRATION",code(f,started.path("handle").asString()));body.put("proof",jar.values.get(cookie(f,"registration-proof")).getValue());
        mvc.perform(postRequest(f,jar,"/email/verifications",body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));body.remove("proof");
        mvc.perform(postRequest(f,jar,"/email/verifications",body).with(r->{r.removeHeader("X-App-CSRF");return r;})).andExpect(status().isForbidden());
        mvc.perform(postRequest(f,jar,"/email/verifications",body).with(r->{r.removeHeader("Origin");return r;})).andExpect(status().isForbidden());
        var nativeClient=fixture(1294,false);var nativeJar=bootstrap(nativeClient);
        for(String kind:List.of("registration-proof","password-reset-proof","login-proof","csrf"))
            mvc.perform(postRequest(nativeClient,nativeJar,"/password/sessions",Map.of("email","x@example.test","password",password)).cookie(new Cookie(cookie(nativeClient,kind),"x".repeat(43))))
                    .andExpect(status().isForbidden());
        assertThat(verify(f,jar,started,"REGISTRATION").path("grant").asString()).hasSize(43);
    }
    @Test void wrongCodesCommitAttemptsAndUnknownResetCannotCreateAccount()throws Exception{
        var f=fixture(1295,false);var jar=bootstrap(f);var started=challenge(f,jar,"REGISTRATION","owner@example.test");String valid=code(f,started.path("handle").asString());
        String wrong=valid.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++)mvc.perform(postRequest(f,jar,"/email/verifications",verification(f,started,"REGISTRATION",wrong))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(postRequest(f,jar,"/email/verifications",verification(f,started,"REGISTRATION",valid))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(409));
        var reset=challenge(f,jar,"PASSWORD_RESET","unknown@example.test");var grant=verify(f,jar,reset,"PASSWORD_RESET");
        mvc.perform(postRequest(f,jar,"/password/resets",credential(f,reset,grant,password))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        assertThat(job(f.tenant(),()->credentials.selectCount(null))).isZero();
    }
    @Test void grantsCannotChangePurposeClientOrTenantAndRegistrationDoesNotAllowBindingPurpose()throws Exception{
        var f=fixture(1296,false);var jar=bootstrap(f);var started=challenge(f,jar,"REGISTRATION","owner@example.test");var grant=verify(f,jar,started,"REGISTRATION");var body=credential(f,started,grant,password);
        mvc.perform(postRequest(f,jar,"/password/resets",body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var foreign=fixture(1297,false);mvc.perform(postRequest(foreign,new Jar(),"/registrations",body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var other=admin(f.tenant(),()->clients.create(new IdentityClientService.Input(f.realm().id(),"other","Other","shop",IdentityClientService.Platform.ANDROID,true,false,null,null,Set.of(ORIGIN+"/cb"),Set.of(),900,2592000)));
        mvc.perform(postRequest(new Fixture(f.tenant(),f.realm(),other),new Jar(),"/registrations",body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(postRequest(f,jar,"/email/challenges",Map.of("purpose","EMAIL_BINDING","email","other@example.test","language","en-US"))).andExpect(status().isBadRequest());
        assertThat(success(f,jar,"/registrations",body).path("subjectId").asString()).isNotBlank();
        mvc.perform(postRequest(f,jar,"/registrations",body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
    }
    @Test void purposeCookieIsHttpOnlyAndRespectsTheChallengeDeadlineWithoutLeakingCodeOrPassword()throws Exception{
        var f=fixture(1298,true);var jar=bootstrap(f);
        var response=mvc.perform(postRequest(f,jar,"/email/challenges",Map.of("purpose","REGISTRATION","email","owner@example.test","language","en-US")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(response);String handle=data(response).path("handle").asString();String code=code(f,handle);
        assertThat(response.getResponse().getContentAsString()).doesNotContain(code,"owner@example.test","\"proof\":");
        var proof=response.getResponse().getHeaders("Set-Cookie").stream().filter(c->c.startsWith(cookie(f,"registration-proof")+"=")).findFirst().orElseThrow();
        assertThat(proof).contains("Secure","HttpOnly","Path=/","SameSite=Lax","Max-Age=300").doesNotContain("Domain=");
        now=now.plusSeconds(290);var grant=verify(f,jar,data(response),"REGISTRATION");now=now.plusSeconds(5);
        var completed=mvc.perform(postRequest(f,jar,"/registrations",credential(f,data(response),grant,password))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        assertThat(completed.getResponse().getContentAsString()).doesNotContain(password,"\"accessToken\":","\"refreshToken\":");
    }
}
