package com.sma.admin.openapi;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.service.ApplicationService;
import com.sma.admin.openapi.internal.persistence.*;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.auth.internal.service.SessionIssuer;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class OpenAuthenticationTests {
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.sma.admin.mail.MailReceiptApi mailReceipts;
 @Test void receiptBridgeRequiresBodySignatureEvenForUnsignedClientsAndRejectsReplayAndCrossTenantSigning()throws Exception{
  var app=create(5292,false);String token=issue(app,false),path="/open/mail/receipts";
  String content="{\"application\":\"shop\",\"profileId\":123,\"provider\":\"test-smtp\",\"eventId\":\"event-1\",\"messageId\":\"sma-12345678-1234-1234-1234-123456789012@mail.invalid\",\"type\":\"DELIVERED\",\"occurredAt\":\"2026-11-11T00:00:00Z\"}";
  org.mockito.Mockito.doAnswer(i->{
   var actor=ActorContext.require();assertThat(actor.tenantId()).isEqualTo(5292);assertThat(actor.subject()).isEqualTo("app:"+app.appId());
   return new com.sma.admin.mail.MailReceiptApi.Result(100,"DELIVERED",false);
  }).when(mailReceipts).accept(org.mockito.ArgumentMatchers.any());
  mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content).header("Authorization","Bearer "+token))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("openapi.error.invalid-signature"));
  String stamp=Long.toString(Instant.now().getEpochSecond()),requestNonce=nonce();
  mvc.perform(signed("POST",path,"","",content,app,token,stamp,requestNonce)).andExpect(status().isOk()).andExpect(jsonPath("$.data.receiptState").value("DELIVERED"));
  mvc.perform(signed("POST",path,"","",content,app,token,stamp,requestNonce)).andExpect(status().isConflict());
  mvc.perform(signed("POST",path,"","",content,app,token,stamp,nonce()).content(content.replace("DELIVERED","BOUNCED"))).andExpect(status().isForbidden());
  mvc.perform(signed("POST",path,"","",content,app,token,Long.toString(Instant.now().minusSeconds(301).getEpochSecond()),nonce())).andExpect(status().isForbidden());
  var other=create(5293,false);
  mvc.perform(signed("POST",path,"","",content,other,token,stamp,nonce())).andExpect(status().isForbidden());
  org.mockito.Mockito.verify(mailReceipts,org.mockito.Mockito.times(1)).accept(org.mockito.ArgumentMatchers.any());
 }
 @Autowired com.sma.admin.tenant.internal.persistence.TenantRecordMapper tenantRows;
 @Test void tenantSuspensionAndResumeCannotReviveOldMachineTokens()throws Exception{
  var app=create(5291,false);String token=issue(app,false);
  var tenant=tenantRows.selectById(5291L);tenant.setStatus("SUSPENDED");tenant.setSecurityGeneration(2L);tenantRows.updateById(tenant);
  mvc.perform(get("/open/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(post("/open/token").contentType(MediaType.APPLICATION_JSON).content(body(app))).andExpect(status().isUnauthorized());
  tenant.setStatus("ACTIVE");tenant.setSecurityGeneration(3L);tenantRows.updateById(tenant);
  mvc.perform(get("/open/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  String fresh=issue(app,false);mvc.perform(get("/open/me").header("Authorization","Bearer "+fresh)).andExpect(status().isOk());
 }
 @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired ApplicationService apps;@Autowired TokenMapper tokens;@Autowired UserService users;@Autowired SessionIssuer sessions;
 private ApplicationService.Credentials create(long tenant,boolean signed){return Users.as(Users.principal(101,tenant,DataAccess.unrestricted()),()->apps.create(new ApplicationService.Input("App "+tenant,1,null,true,signed)));}
 private String body(ApplicationService.Credentials app){return "{\"appId\":\""+app.appId()+"\",\"appSecret\":\""+app.appSecret()+"\"}";}
 private String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
 private String nonce(){return UUID.randomUUID().toString();}
 private MockHttpServletRequestBuilder signed(String method,String path,String rawQuery,String canonicalQuery,String content,ApplicationService.Credentials app,String token,String timestamp,String nonce)throws Exception{
  String canonical=method+"\n"+path+"\n"+canonicalQuery+"\n"+timestamp+"\n"+nonce+"\n"+hash(content);
  var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(app.appSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  var request=request(HttpMethod.valueOf(method),URI.create(path+(rawQuery.isEmpty()?"":"?"+rawQuery))).contentType(MediaType.APPLICATION_JSON).content(content)
   .header("X-App-Id",app.appId()).header("X-Timestamp",timestamp).header("X-Nonce",nonce).header("X-Signature",HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)))).header("Accept-Language","en-US");
  if(token!=null)request.header("Authorization","Bearer "+token);return request;
 }
 private String issue(ApplicationService.Credentials app,boolean signed)throws Exception{
  var request=signed?signed("POST","/open/token","","",body(app),app,null,Long.toString(Instant.now().getEpochSecond()),nonce()):post("/open/token").contentType(MediaType.APPLICATION_JSON).content(body(app));
  String response=mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();return json.readTree(response).path("data").path("token").asString();
 }
 @Test void signedRequestsBindBodyQueryAndTenantAndDatabaseNonceRejectsReplay()throws Exception{
  var app=create(291,true);String token=issue(app,true),now=Long.toString(Instant.now().getEpochSecond()),nonce=nonce();
  mvc.perform(signed("GET","/open/me","z=last&a=two+words&a=one","a=one&a=two%20words&z=last","",app,token,now,nonce)).andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId").value("291"));
  mvc.perform(signed("GET","/open/me","z=last&a=two+words&a=one","a=one&a=two%20words&z=last","",app,token,now,nonce)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("openapi.error.replay"));
  var modified=signed("POST","/open/echo","","","{\"amount\":1}",app,token,now,nonce()).content("{\"amount\":2}");
  mvc.perform(modified).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("openapi.error.invalid-signature"));
  mvc.perform(signed("POST","/open/echo","","","{\"amount\":2}",app,token,now,nonce())).andExpect(status().isOk()).andExpect(jsonPath("$.data.payload.amount").value(2));
  mvc.perform(signed("GET","/open/me","","","",app,token,Long.toString(Instant.now().minusSeconds(600).getEpochSecond()),nonce())).andExpect(status().isForbidden());
  mvc.perform(get("/api/iam/users").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  String expected=hash(token);TenantScope.within(291,()->{var stored=tokens.selectOne(new LambdaQueryWrapper<TokenEntity>().eq(TokenEntity::getTokenHash,expected));assertThat(stored).isNotNull();assertThat(stored.getTokenHash()).isNotEqualTo(token);return null;});
 }
 @Test void secretRotationDisableExpiryAndRevocationInvalidateApplicationTokens()throws Exception{
  var app=create(292,false);String token=issue(app,false);
  mvc.perform(get("/open/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  var rotated=Users.as(Users.principal(101,292,DataAccess.unrestricted()),()->apps.rotate(app.id()));
  mvc.perform(get("/open/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(post("/open/token").contentType(MediaType.APPLICATION_JSON).content(body(app))).andExpect(status().isUnauthorized());
  String fresh=issue(rotated,false);
  Users.as(Users.principal(101,292,DataAccess.unrestricted()),()->{apps.update(app.id(),new ApplicationService.Input("App 292",1,null,false,false));return null;});
  mvc.perform(get("/open/me").header("Authorization","Bearer "+fresh)).andExpect(status().isUnauthorized());
  Users.as(Users.principal(101,292,DataAccess.unrestricted()),()->{apps.update(app.id(),new ApplicationService.Input("App 292",1,null,true,false));return null;});
  String revocable=issue(rotated,false);mvc.perform(post("/open/revoke").header("Authorization","Bearer "+revocable)).andExpect(status().isOk());mvc.perform(get("/open/me").header("Authorization","Bearer "+revocable)).andExpect(status().isUnauthorized());
  String expired=issue(rotated,false),hashed=hash(expired);TenantScope.within(292,()->{var row=tokens.selectOne(new LambdaQueryWrapper<TokenEntity>().eq(TokenEntity::getTokenHash,hashed));row.setExpiresAt(Instant.now().minusSeconds(1));tokens.updateById(row);return null;});
  mvc.perform(get("/open/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
 }
 @Test void simultaneousNonceReplayHasOneWinnerAndNonceCaseIsSignificant()throws Exception{
  var app=create(293,true);String token=issue(app,true),now=Long.toString(Instant.now().getEpochSecond()),nonce=nonce();
  try(var executor=Executors.newFixedThreadPool(2)){
   Callable<Integer> call=()->mvc.perform(signed("GET","/open/me","","","",app,token,now,nonce)).andReturn().getResponse().getStatus();
   var results=executor.invokeAll(List.of(call,call));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);
  }
  for(String value:List.of("CaseSensitiveNonceA1","caseSensitiveNonceA1"))mvc.perform(signed("GET","/open/me","","","",app,token,now,value)).andExpect(status().isOk());
 }
 @Test void humanTokensAreRejectedByTheApplicationChainAndInvalidCredentialsAreRateLimited()throws Exception{
  String human=Users.as(Users.principal(101,294,DataAccess.unrestricted()),()->{
   var user=users.create(new UserService.Create(new UserService.Input("open-human","Human",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,null),"Q743fa13c7"));
   return sessions.issue(users.findById(user.id()).orElseThrow(),false,Duration.ofMinutes(5),"127.0.0.1","test").token();
  });
  mvc.perform(get("/open/me").header("Authorization","Bearer "+human)).andExpect(status().isUnauthorized());
  String invalid="{\"appId\":\""+"b".repeat(32)+"\",\"appSecret\":\""+"c".repeat(43)+"\"}";
  for(int i=0;i<10;i++)mvc.perform(post("/open/token").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isUnauthorized());
  mvc.perform(post("/open/token").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isTooManyRequests());
 }
}
