package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import jakarta.servlet.http.Cookie;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc @Import(IdentityApplicationHttpTests.Probes.class)
class IdentityApplicationHttpTests {
    @TestConfiguration(proxyBeanMethods=false) static class Probes {@Bean ShopProbeController shopProbe(){return new ShopProbeController();}}
    @TestComponent @RestController @RequestMapping("/app/shop-probe")
    static class ShopProbeController {@GetMapping("/profile") @PermitApplicationUser("shop") public ApiResponse<String> profile(){return ApiResponse.ok("shop");}}
    private static Path keyFile;private static RSAKey signingKey;
    @BeforeAll static void rsa()throws Exception{signingKey=new RSAKeyGenerator(2048).keyID("http-login").generate();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-p09-http-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;
    @Autowired com.sma.admin.iam.internal.service.UserService users;@Autowired com.sma.admin.auth.internal.service.SessionIssuer adminSessions;
    @Autowired com.sma.admin.openapi.internal.service.ApplicationService machines;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean GoogleHttpTransport transport;
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");private final String verifier="v".repeat(43),origin="https://app.example.test",callback="https://app.example.test/cb",audience="123456-http.apps.googleusercontent.com";
    @BeforeEach void setup(){doReturn(now).when(clock).now();doReturn(new GoogleHttpTransport.JwksDocument(new JWKSet(signingKey.toPublicJWK()).toString(),Duration.ofHours(1))).when(transport).fetchKeys();}
    private record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client){}
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(101,"identity-http-admin",tenant,"admin",null,false,DataAccess.unrestricted(),
            Set.of("identity:realm:manage","identity:client:manage","identity:client:view"),"UTC"),action);}
    private Fixture fixture(long tenant,boolean web,String application,IdentityClientService.CookieSameSite sameSite){
        var realm=admin(tenant,()->realms.create(new IdentityRealmService.Input("r-"+UUID.randomUUID().toString().substring(0,8),"Realm",true,true,true)));
        var client=admin(tenant,()->clients.create(new IdentityClientService.Input(realm.id(),"c-"+UUID.randomUUID().toString().substring(0,8),"App",application,
                web?IdentityClientService.Platform.WEB:IdentityClientService.Platform.ANDROID,true,true,audience,web?"http-test-secret-value".toCharArray():null,
                Set.of(callback),web?Set.of(origin):Set.of(),900,2592000,sameSite)));
        return new Fixture(tenant,realm,client);
    }
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder,Fixture f){
        builder.queryParam("appClient",f.client.loginHandle()).header("Host","t"+f.tenant+"-admin.test").secure(true)
                .with(r->{r.setScheme("https");r.setServerPort(443);return r;});
        if(f.client.platform()==IdentityClientService.Platform.WEB)builder.header("Origin",origin);return builder;
    }
    private JsonNode data(MvcResult result){return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
    private String cookieName(Fixture f,String kind){return "__Host-sma-"+kind+"-"+f.client.loginHandle();}
    private static final class Jar {
        final Map<String,Cookie> cookies=new HashMap<>();
        void accept(MvcResult result){for(String value:result.getResponse().getHeaders("Set-Cookie")){
            String[] pair=value.split(";",2)[0].split("=",2);if(value.contains("Max-Age=0") || pair.length<2 || pair[1].isEmpty())cookies.remove(pair[0]);
            else cookies.put(pair[0],new Cookie(pair[0],pair[1]));}}
        Cookie[] all(){return cookies.values().toArray(Cookie[]::new);}
        String value(String name){return cookies.get(name).getValue();}
    }
    private String token(String nonce,String subject)throws Exception{
        return token(nonce,subject,null);
    }
    private String token(String nonce,String subject,String party)throws Exception{
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .subject(subject).audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).claim("nonce",nonce).claim("azp",party).build());
        jwt.sign(new RSASSASigner(signingKey));return jwt.serialize();
    }
    private String startBody(){return json.writeValueAsString(Map.of("callbackUri",callback,"flow","ID_TOKEN","pkceChallenge",GoogleProtocol.challenge(verifier)));}
    private String completeBody(JsonNode started,String proof,String subject)throws Exception{
        var body=json.createObjectNode().put("state",started.path("state").asString()).put("verifier",verifier).put("callbackUri",callback).put("flow","ID_TOKEN")
                .put("credential",token(started.path("nonce").asString(),subject));if(proof!=null)body.put("proof",proof);return json.writeValueAsString(body);
    }
    private MvcResult bootstrap(Fixture f,Jar jar)throws Exception{
        var request=request(get("/app/identity/bootstrap"),f);if(!jar.cookies.isEmpty())request.cookie(jar.all());var result=mvc.perform(request)
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(result);return result;}
    private JsonNode nativeLogin(Fixture f,String subject)throws Exception{
        var started=mvc.perform(request(post("/app/identity/google/transactions"),f).contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();assertThat(started.getResponse().getHeaders("Set-Cookie")).isEmpty();var first=data(started);
        var result=mvc.perform(request(post("/app/identity/google/completions"),f).contentType(MediaType.APPLICATION_JSON).content(completeBody(first,first.path("proof").asString(),subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();return data(result);
    }
    private JsonNode webLogin(Fixture f,Jar jar,String subject)throws Exception{
        bootstrap(f,jar);var started=mvc.perform(request(post("/app/identity/google/transactions"),f).cookie(jar.all()).header("X-App-CSRF",jar.value(cookieName(f,"csrf")))
                .contentType(MediaType.APPLICATION_JSON).content(startBody())).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(started);
        assertThat(started.getResponse().getContentAsString()).doesNotContain("\"proof\":");
        var result=mvc.perform(request(post("/app/identity/google/completions"),f).cookie(jar.all()).header("X-App-CSRF",jar.value(cookieName(f,"csrf")))
                .contentType(MediaType.APPLICATION_JSON).content(completeBody(data(started),null,subject))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"accessToken\":","\"refreshToken\":");return data(result);
    }
    @Test void webLoginUsesSecureClientCookiesCsrfAndSelfLogoutWithoutReturningCredentials()throws Exception{
        var f=fixture(1101,true,"shop",IdentityClientService.CookieSameSite.LAX);var jar=new Jar();var issued=webLogin(f,jar,"web-user");
        assertThat(issued.path("mode").asString()).isEqualTo("WEB");assertThat(jar.cookies).containsKeys(cookieName(f,"access"),cookieName(f,"refresh"),cookieName(f,"csrf")).doesNotContainKey(cookieName(f,"login-proof"));
        mvc.perform(request(get("/app/identity/sessions/current"),f).cookie(jar.all())).andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectId").value(issued.path("subjectId").asString()));
        mvc.perform(request(delete("/app/identity/sessions/current"),f).cookie(jar.all()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        var done=mvc.perform(request(delete("/app/identity/sessions/current"),f).cookie(jar.all()).header("X-App-CSRF",jar.value(cookieName(f,"csrf")))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        for(String header:done.getResponse().getHeaders("Set-Cookie"))assertThat(header).contains("Secure","Path=/","SameSite=Lax","Max-Age=0").doesNotContain("Domain=");
        mvc.perform(request(get("/app/identity/sessions/current"),f).cookie(jar.all())).andExpect(status().isUnauthorized());
    }
    @Test void webRefreshRotatesCookieAndCsrfAndOldProofRevokesTheFamily()throws Exception{
        var f=fixture(1102,true,"shop",IdentityClientService.CookieSameSite.NONE);var jar=new Jar();webLogin(f,jar,"refresh-web");Cookie[] old=jar.all();String oldCsrf=jar.value(cookieName(f,"csrf"));
        var response=mvc.perform(request(post("/app/identity/sessions/refresh"),f).cookie(old).header("X-App-CSRF",oldCsrf).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();jar.accept(response);
        assertThat(response.getResponse().getContentAsString()).doesNotContain("\"accessToken\":","\"refreshToken\":");assertThat(jar.value(cookieName(f,"csrf"))).isNotEqualTo(oldCsrf);
        assertThat(response.getResponse().getHeaders("Set-Cookie")).allSatisfy(v->assertThat(v).contains("Secure","Path=/","SameSite=None").doesNotContain("Domain="));
        assertThat(response.getResponse().getHeaders("Set-Cookie").stream().filter(v->v.startsWith(cookieName(f,"access")+"=") || v.startsWith(cookieName(f,"refresh")+"="))).allSatisfy(v->assertThat(v).contains("HttpOnly"));
        mvc.perform(request(post("/app/identity/sessions/refresh"),f).cookie(old).header("X-App-CSRF",oldCsrf).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(request(get("/app/identity/sessions/current"),f).cookie(jar.all())).andExpect(status().isUnauthorized());
    }
    @Test void nativeTokensAreBearerOnlyAndAudienceClientTenantBound()throws Exception{
        var f=fixture(1103,false,"shop",null);var issued=nativeLogin(f,"native-user");String access=issued.path("accessToken").asString();assertThat(access).hasSize(43);
        mvc.perform(request(get("/app/shop-probe/profile"),f).header("Authorization","Bearer "+access)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("shop"));
        var otherClient=fixture(1103,false,"other",null);var other=nativeLogin(otherClient,"other-user");
        mvc.perform(request(get("/app/shop-probe/profile"),otherClient).header("Authorization","Bearer "+other.path("accessToken").asString())).andExpect(status().isForbidden());
        mvc.perform(request(get("/app/identity/sessions/current"),otherClient).header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        var otherTenant=fixture(1104,false,"shop",null);
        mvc.perform(request(get("/app/identity/sessions/current"),otherTenant).header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(request(get("/app/identity/sessions/current"),f).cookie(new Cookie(cookieName(f,"access"),access))).andExpect(status().isForbidden());
        mvc.perform(request(get("/app/identity/sessions/current"),f).header("Authorization","Bearer "+access).header("Origin",origin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Host","t1103-admin.test").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
    }
    @Test void nativeRefreshExpiresOldAccessAndLogoutRejectsFurtherRefresh()throws Exception{
        var f=fixture(1105,false,"shop",null);var issued=nativeLogin(f,"native-refresh");
        var response=mvc.perform(request(post("/app/identity/sessions/refresh"),f).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken",issued.path("refreshToken").asString())))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        var next=data(response);assertThat(response.getResponse().getHeaders("Set-Cookie")).isEmpty();
        mvc.perform(request(get("/app/identity/sessions/current"),f).header("Authorization","Bearer "+issued.path("accessToken").asString())).andExpect(status().isUnauthorized());
        mvc.perform(request(delete("/app/identity/sessions/current"),f).header("Authorization","Bearer "+next.path("accessToken").asString())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(request(post("/app/identity/sessions/refresh"),f).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("refreshToken",next.path("refreshToken").asString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
    }
    @Test void corsPreflightOriginHttpsMediaBodyAndAmbiguousSelectorsFailClosed()throws Exception{
        var f=fixture(1106,true,"shop",IdentityClientService.CookieSameSite.NONE);
        mvc.perform(request(options("/app/identity/google/transactions"),f).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-app-csrf"))
                .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin",origin)).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(request(options("/app/identity/google/transactions"),f).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","x-unregistered"))
                .andExpect(status().isForbidden());
        mvc.perform(request(get("/app/identity/bootstrap"),f).with(r->{r.removeHeader("Origin");r.addHeader("Origin","https://evil.example");return r;})).andExpect(status().isForbidden());
        mvc.perform(request(get("/app/identity/bootstrap"),f).with(r->{r.setScheme("http");r.setSecure(false);return r;})).andExpect(status().isForbidden());
        mvc.perform(request(get("/app/identity/bootstrap"),f).queryParam("appClient",f.client.loginHandle())).andExpect(status().isBadRequest());
        mvc.perform(request(get("/app/identity/bootstrap"),f).with(r->{r.removeHeader("Host");r.addHeader("Host","unknown-tenant.example.test");return r;})).andExpect(status().isNotFound());
        mvc.perform(request(post("/app/identity/google/transactions"),f).contentType(MediaType.TEXT_PLAIN).content(startBody())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(request(post("/app/identity/google/transactions"),f).contentType(MediaType.APPLICATION_JSON).content("x".repeat(65537))).andExpect(status().isPayloadTooLarge());
    }
    @Test void browserProofCannotBeInjectedInJsonAndCsrfCannotBeSuppliedAsAQueryParameter()throws Exception{
        var f=fixture(1107,true,"shop",null);var jar=new Jar();bootstrap(f,jar);String csrf=jar.value(cookieName(f,"csrf"));
        mvc.perform(request(post("/app/identity/google/transactions"),f).cookie(jar.all()).queryParam("_csrf",csrf).contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isForbidden());
        var started=mvc.perform(request(post("/app/identity/google/transactions"),f).cookie(jar.all()).header("X-App-CSRF",csrf).contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isOk()).andReturn();jar.accept(started);var first=data(started);
        mvc.perform(request(post("/app/identity/google/completions"),f).cookie(jar.all()).header("X-App-CSRF",csrf).contentType(MediaType.APPLICATION_JSON)
                .content(completeBody(first,jar.value(cookieName(f,"login-proof")),"injected"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        var noProof=jar.cookies.values().stream().filter(c->!c.getName().equals(cookieName(f,"login-proof"))).toArray(Cookie[]::new);
        mvc.perform(request(post("/app/identity/google/completions"),f).cookie(noProof).header("X-App-CSRF",csrf).contentType(MediaType.APPLICATION_JSON).content(completeBody(first,null,"missing")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(request(post("/app/identity/google/completions"),f).cookie(jar.all()).header("X-App-CSRF",csrf).contentType(MediaType.APPLICATION_JSON).content(completeBody(first,null,"valid")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void adminAndMachineCredentialsNeverBecomeApplicationUserSessions()throws Exception{
        var f=fixture(1108,false,"shop",null);
        String human=admin(f.tenant,()->{
            var user=users.create(new com.sma.admin.iam.internal.service.UserService.Create(new com.sma.admin.iam.internal.service.UserService.Input("p09-http-admin","Admin",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q743fa13c7"));
            return adminSessions.issue(users.findById(user.id()).orElseThrow(),false,Duration.ofMinutes(5),"127.0.0.1","test").token();
        });
        mvc.perform(request(get("/app/identity/sessions/current"),f).header("Authorization","Bearer "+human)).andExpect(status().isUnauthorized());
        var machine=admin(f.tenant,()->machines.create(new com.sma.admin.openapi.internal.service.ApplicationService.Input("P09 machine",1,null,true,false)));
        var credentials=json.createObjectNode().put("appId",machine.appId()).put("appSecret",machine.appSecret());
        var machineToken=mvc.perform(post("/open/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(credentials)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        mvc.perform(request(get("/app/identity/sessions/current"),f).header("Authorization","Bearer "+data(machineToken).path("token").asString())).andExpect(status().isUnauthorized());
    }
    @Test void nativeHybridGoogleLoginRequiresTheConfiguredPresenterEvenWithValidSignatureAndAudience()throws Exception{
        var base=fixture(1110,false,"shop",null);String party="123456-native-sdk.apps.googleusercontent.com";
        var c=base.client;
        var updated=admin(base.tenant,()->clients.update(c.id(),c.version(),new IdentityClientService.Input(c.realmId(),c.code(),c.name(),c.application(),c.platform(),true,true,
                audience,null,Set.copyOf(c.callbackUris()),Set.of(),900,2592000,null,party)));
        var f=new Fixture(base.tenant,base.realm,updated);
        for(String actual:List.of(audience,party)){
            var start=mvc.perform(request(post("/app/identity/google/transactions"),f).contentType(MediaType.APPLICATION_JSON).content(startBody()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();var first=data(start);
            var body=json.readTree(completeBody(first,first.path("proof").asString(),"hybrid"));
            ((tools.jackson.databind.node.ObjectNode)body).put("credential",token(first.path("nonce").asString(),"hybrid",actual));
            mvc.perform(request(post("/app/identity/google/completions"),f).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(actual.equals(party)?0:401));
        }
    }
    @Test void twoWebClientsOnOneHostKeepSeparateCookiesAndCannotExchangeThem()throws Exception{
        var first=fixture(1109,true,"shop",null);var second=fixture(1109,true,"other",null);var jar=new Jar();
        var a=webLogin(first,jar,"first-web");var b=webLogin(second,jar,"second-web");
        assertThat(jar.cookies).containsKeys(cookieName(first,"access"),cookieName(second,"access"));
        mvc.perform(request(get("/app/identity/sessions/current"),first).cookie(jar.all())).andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectId").value(a.path("subjectId").asString()));
        mvc.perform(request(get("/app/identity/sessions/current"),second).cookie(jar.all())).andExpect(status().isOk()).andExpect(jsonPath("$.data.subjectId").value(b.path("subjectId").asString()));
        var wrong=new Cookie(cookieName(second,"access"),jar.value(cookieName(first,"access")));
        mvc.perform(request(get("/app/identity/sessions/current"),second).cookie(wrong)).andExpect(status().isUnauthorized());
    }
}
