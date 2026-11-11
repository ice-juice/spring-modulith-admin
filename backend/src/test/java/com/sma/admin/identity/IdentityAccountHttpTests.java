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
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
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
class IdentityAccountHttpTests {
    private static Path keyFile;private static RSAKey signingKey;
    @BeforeAll static void signing()throws Exception{signingKey=new RSAKeyGenerator(2048).keyID("account-http").generate();}
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-account-http-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;
    @Autowired IdentityEmailConfigurationService configurations;@Autowired MailProfileService profiles;@Autowired MailTemplateService templates;
    @Autowired IdentityEmailAccountService accounts;@Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;
    @Autowired MailDeliveryMapper mailRows;@Autowired MailOutboxService outbox;@Autowired IdentityPasswordLoginCoordinator passwords;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean GoogleHttpTransport transport;
    private Instant now;private Mailbox mailbox;private final String password="Q7d4e10594",verifier="v".repeat(43),audience="123456-email.apps.googleusercontent.com";
    @BeforeEach void setup(){now=Instant.parse("2026-11-12T00:00:00Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();
        doReturn(new GoogleHttpTransport.JwksDocument(new JWKSet(signingKey.toPublicJWK()).toString(),Duration.ofHours(1))).when(transport).fetchKeys();
        mailbox=new Mailbox(accounts,challenges,challengeRows,mailRows,outbox);
    }
    private Fixture fixture(long tenant){return EmailIdentityTestSupport.fixture(tenant,realms,clients,configurations,profiles,templates);}
    private Fixture nativeClient(Fixture base,String code){return admin(base.tenant(),()->{
        var client=clients.create(new IdentityClientService.Input(base.realm().id(),code,code,"shop",IdentityClientService.Platform.ANDROID,true,true,audience,null,Set.of(ORIGIN+"/cb"),Set.of(),900,2592000));
        configurations.put(client.id(),"en-US",0,new IdentityEmailConfigurationService.Input(true,"registration","password-reset","email-binding"));return new Fixture(base.tenant(),base.realm(),client);
    });}
    private boolean web(Fixture f){return f.client().platform()==IdentityClientService.Platform.WEB;}
    private String cookie(Fixture f,String kind){return "__Host-sma-"+kind+"-"+f.client().loginHandle();}
    private static class Account {
        final Map<String,Cookie> cookies=new HashMap<>();final String bearer;
        Account(String bearer){this.bearer=bearer;}
        void accept(MvcResult result){for(String header:result.getResponse().getHeaders("Set-Cookie")){
            var pair=header.split(";",2)[0].split("=",2);if(header.contains("Max-Age=0") || pair.length<2 || pair[1].isEmpty())cookies.remove(pair[0]);else cookies.put(pair[0],new Cookie(pair[0],pair[1]));}}
        Cookie[] all(){return cookies.values().toArray(Cookie[]::new);}
    }
    private Account from(Fixture f,IdentitySessionStore.Issued issued){
        var account=new Account(web(f)?null:issued.accessToken());if(web(f)){account.cookies.put(cookie(f,"access"),new Cookie(cookie(f,"access"),issued.accessToken()));
            account.cookies.put(cookie(f,"refresh"),new Cookie(cookie(f,"refresh"),issued.refreshToken()));account.cookies.put(cookie(f,"csrf"),new Cookie(cookie(f,"csrf"),issued.csrfToken()));}return account;
    }
    private Account register(Fixture f,String email){return from(f,mailbox.register(f,email,password));}
    private Account login(Fixture f,String email){return from(f,job(f.tenant(),()->passwords.login(new IdentityPasswordLoginCoordinator.Login(f.client().loginHandle(),email,password,web(f)?ORIGIN:null,"10.0.0.1"))));}
    private MockHttpServletRequestBuilder request(Fixture f,Account account,MockHttpServletRequestBuilder builder){
        builder.queryParam("appClient",f.client().loginHandle()).header("Host","t"+f.tenant()+"-admin.test").secure(true).with(r->{r.setScheme("https");r.setServerPort(443);return r;});
        if(web(f))builder.header("Origin",ORIGIN);
        if(account!=null){if(!account.cookies.isEmpty())builder.cookie(account.all());if(account.bearer!=null)builder.header("Authorization","Bearer "+account.bearer);
            if(web(f) && account.cookies.containsKey(cookie(f,"csrf")))builder.header("X-App-CSRF",account.cookies.get(cookie(f,"csrf")).getValue());}
        return builder;
    }
    private MockHttpServletRequestBuilder postRequest(Fixture f,Account account,String path,Map<String,?> input){return request(f,account,post("/app/identity/account"+path)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input));}
    private JsonNode data(MvcResult result){return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
    private JsonNode send(Fixture f,Account account,String path,Map<String,?> input)throws Exception{
        var result=mvc.perform(postRequest(f,account,path,input)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();account.accept(result);return data(result);
    }
    private JsonNode methods(Fixture f,Account account)throws Exception{
        var result=mvc.perform(request(f,account,get("/app/identity/account/methods"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        account.accept(result);if(web(f))assertThat(account.cookies).containsKey(cookie(f,"csrf"));assertThat(result.getRequest().getSession(false)).isNull();return data(result);
    }
    private String proof(Fixture f,Account account,String action,String target)throws Exception{return send(f,account,"/reauthentication/password",Map.of("action",action,"target",target,"password",password)).path("grant").asString();}
    private String code(Fixture f,String handle){return job(f.tenant(),()->{
        String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(handle.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        var row=challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>().eq(IdentityEmailChallengeEntity::getHandleHash,hash));String body=outbox.payload(mailRows.selectById(row.getDeliveryId())).body();
        var match=java.util.regex.Pattern.compile("Code ([0-9]{6}),").matcher(body);assertThat(match.find()).isTrue();return match.group(1);
    });}
    private Map<String,Object> verification(Fixture f,JsonNode started){var input=new HashMap<String,Object>();input.put("handle",started.path("handle").asString());input.put("code",code(f,started.path("handle").asString()));
        if(!web(f))input.put("proof",started.path("proof").asString());return input;}
    private Map<String,Object> emailComplete(Fixture f,JsonNode started,JsonNode verified){var input=new HashMap<String,Object>();input.put("grant",verified.path("grant").asString());if(!web(f))input.put("proof",started.path("proof").asString());return input;}
    private Map<String,Object> googleStart(){return Map.of("callbackUri",ORIGIN+"/cb","flow","ID_TOKEN","pkceChallenge",GoogleProtocol.challenge(verifier));}
    private Map<String,Object> googleComplete(Fixture f,JsonNode started,String sub)throws Exception{
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .subject(sub).audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).claim("nonce",started.path("nonce").asString())
                .claim("email","same@example.test").claim("email_verified",true).build());jwt.sign(new RSASSASigner(signingKey));
        var input=new HashMap<String,Object>();input.put("state",started.path("state").asString());input.put("verifier",verifier);input.put("callbackUri",ORIGIN+"/cb");input.put("flow","ID_TOKEN");input.put("credential",jwt.serialize());
        if(!web(f))input.put("proof",started.path("proof").asString());return input;
    }
    private JsonNode linkGoogle(Fixture f,Account account,String sub)throws Exception{
        String grant=proof(f,account,"GOOGLE_BINDING","google");var started=send(f,account,"/google-links/transactions",Map.of("grant",grant,"google",googleStart()));
        return send(f,account,"/google-links/completions",googleComplete(f,started,sub));
    }
    @Test void methodsAreOnlyForCurrentSubjectAndNeverExposeCredentialOrProviderSecrets()throws Exception{
        var f=fixture(1311);var first=register(f,"first@example.test");var second=register(f,"second@example.test");
        var result=mvc.perform(request(f,first,get("/app/identity/account/methods")).queryParam("subjectId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.verifiedEmail").value("first@example.test")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("second@example.test","passwordHash","emailEnvelope","externalSubject",password);
        assertThat(methods(f,second).path("verifiedEmail").asString()).isEqualTo("second@example.test");
        mvc.perform(request(f,null,get("/app/identity/account/methods"))).andExpect(status().isUnauthorized());
        var nativeF=nativeClient(f,"native");var nativeAccount=login(nativeF,"first@example.test");var otherClient=nativeClient(f,"other");
        mvc.perform(request(otherClient,nativeAccount,get("/app/identity/account/methods"))).andExpect(status().isUnauthorized());
        var foreign=nativeClient(fixture(1312),"native");mvc.perform(request(foreign,nativeAccount,get("/app/identity/account/methods"))).andExpect(status().isUnauthorized());
    }
    @Test void webEmailBindingUsesOriginalProofAndHttpOnlyCookieThenRevokesSessions()throws Exception{
        var f=fixture(1313);var account=register(f,"owner@example.test");String grant=proof(f,account,"EMAIL_BINDING","new@example.test");
        mvc.perform(postRequest(f,account,"/email-bindings/challenges",Map.of("grant",grant,"email","wrong@example.test","language","en-US"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var started=send(f,account,"/email-bindings/challenges",Map.of("grant",grant,"email","new@example.test","language","en-US"));assertThat(started.has("proof")).isFalse();
        var input=verification(f,started);input.put("proof",account.cookies.get(cookie(f,"binding-proof")).getValue());
        mvc.perform(postRequest(f,account,"/email-bindings/verifications",input)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));input.remove("proof");
        var verified=send(f,account,"/email-bindings/verifications",input);Cookie[] old=account.all();
        var result=send(f,account,"/email-bindings/completions",emailComplete(f,started,verified));assertThat(result.path("signInRequired").asBoolean()).isTrue();assertThat(account.cookies).isEmpty();
        mvc.perform(request(f,null,get("/app/identity/account/methods")).cookie(old)).andExpect(status().isUnauthorized());
        assertThat(methods(f,login(f,"new@example.test")).path("verifiedEmail").asString()).isEqualTo("new@example.test");
    }
    @Test void anotherSessionCannotConsumeBindingVerificationEvenWithCopiedProofCookie()throws Exception{
        var f=fixture(1314);var owner=register(f,"owner@example.test");String grant=proof(f,owner,"EMAIL_BINDING","new@example.test");
        var started=send(f,owner,"/email-bindings/challenges",Map.of("grant",grant,"email","new@example.test","language","en-US"));
        var other=login(f,"owner@example.test");other.cookies.put(cookie(f,"binding-proof"),owner.cookies.get(cookie(f,"binding-proof")));
        mvc.perform(postRequest(f,other,"/email-bindings/verifications",verification(f,started))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        var verified=send(f,owner,"/email-bindings/verifications",verification(f,started));
        mvc.perform(postRequest(f,other,"/email-bindings/completions",emailComplete(f,started,verified))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        assertThat(send(f,owner,"/email-bindings/completions",emailComplete(f,started,verified)).path("changed").asBoolean()).isTrue();
    }
    @Test void webPasswordProofLinksGoogleAndGoogleProofCanRemovePassword()throws Exception{
        var f=fixture(1315);var account=register(f,"owner@example.test");var linked=linkGoogle(f,account,"linked-owner");assertThat(linked.path("signInRequired").asBoolean()).isTrue();assertThat(account.cookies).isEmpty();
        var active=login(f,"owner@example.test");assertThat(methods(f,active).path("googleLinks").size()).isEqualTo(1);
        var started=send(f,active,"/reauthentication/google/transactions",Map.of("action","PASSWORD_REMOVAL","target","password","google",googleStart()));
        var verified=send(f,active,"/reauthentication/google/completions",googleComplete(f,started,"linked-owner"));
        var result=mvc.perform(request(f,active,delete("/app/identity/account/password")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("grant",verified.path("grant").asString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.signInRequired").value(true)).andReturn();active.accept(result);assertThat(active.cookies).isEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("linked-owner",password);
    }
    @Test void nativeGoogleLinkAndRemovalUseBearerJsonProofAndPreserveLastMethod()throws Exception{
        var web=fixture(1316);register(web,"owner@example.test");var f=nativeClient(web,"native");var account=login(f,"owner@example.test");
        mvc.perform(request(f,account,get("/app/identity/account/methods")).cookie(new Cookie(cookie(f,"link-google-proof"),"x".repeat(43)))).andExpect(status().isForbidden());
        var link=linkGoogle(f,account,"native-owner");assertThat(link.path("linkId").asString()).isNotBlank();assertThat(account.cookies).isEmpty();
        mvc.perform(request(f,account,get("/app/identity/account/methods"))).andExpect(status().isUnauthorized());
        var active=login(f,"owner@example.test");String id=methods(f,active).path("googleLinks").get(0).path("id").asString();String grant=proof(f,active,"GOOGLE_REMOVAL",id);
        var removed=mvc.perform(request(f,active,delete("/app/identity/account/google-links/"+id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("grant",grant))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.signInRequired").value(true)).andReturn();assertThat(removed.getResponse().getHeaders("Set-Cookie")).isEmpty();
        now=now.plusSeconds(61);var remaining=login(f,"owner@example.test");String last=proof(f,remaining,"PASSWORD_REMOVAL","password");
        mvc.perform(request(f,remaining,delete("/app/identity/account/password")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("grant",last))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(409));assertThat(methods(f,remaining).path("passwordConfigured").asBoolean()).isTrue();
    }
    @Test void googleProofCannotAuthenticateWithDifferentSubAndProtectedOperationsRequireCsrf()throws Exception{
        var f=fixture(1317);var original=register(f,"owner@example.test");linkGoogle(f,original,"owner");var account=login(f,"owner@example.test");
        var started=send(f,account,"/reauthentication/google/transactions",Map.of("action","EMAIL_BINDING","target","new@example.test","google",googleStart()));
        mvc.perform(postRequest(f,account,"/reauthentication/google/completions",googleComplete(f,started,"same-email-other-sub")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(postRequest(f,account,"/reauthentication/password",Map.of("action","GOOGLE_BINDING","target","google","password",password)).with(r->{r.removeHeader("X-App-CSRF");return r;}))
                .andExpect(status().isForbidden());
        mvc.perform(request(f,account,delete("/app/identity/account/password")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("grant","x".repeat(43)))).with(r->{r.removeHeader("X-App-CSRF");return r;}))
                .andExpect(status().isForbidden());
    }
    @Test void nativeEmailBindingCarriesJsonProofAndRevokesBearerWithoutCookieFallback()throws Exception{
        var web=fixture(1318);register(web,"owner@example.test");var f=nativeClient(web,"native");var account=login(f,"owner@example.test");
        String grant=proof(f,account,"EMAIL_BINDING","new@example.test");
        var started=send(f,account,"/email-bindings/challenges",Map.of("grant",grant,"email","new@example.test","language","en-US"));assertThat(started.path("proof").asString()).hasSize(43);
        var verified=send(f,account,"/email-bindings/verifications",verification(f,started));
        assertThat(send(f,account,"/email-bindings/completions",emailComplete(f,started,verified)).path("signInRequired").asBoolean()).isTrue();assertThat(account.cookies).isEmpty();
        mvc.perform(request(f,account,get("/app/identity/account/methods"))).andExpect(status().isUnauthorized());
        assertThat(methods(f,login(f,"new@example.test")).path("verifiedEmail").asString()).isEqualTo("new@example.test");
    }
}
