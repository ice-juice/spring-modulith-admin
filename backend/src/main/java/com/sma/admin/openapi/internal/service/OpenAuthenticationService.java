package com.sma.admin.openapi.internal.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.openapi.*;
import com.sma.admin.openapi.internal.persistence.*;

@Service
public class OpenAuthenticationService {
 private final ApplicationBootstrapMapper bootstrap;private final ApplicationAuthMapper apps;private final TokenBootstrapMapper tokenBootstrap;private final TokenMapper tokens;private final SecretCipher secrets;private final SignatureVerifier signatures;private final OpenTokenIssuer issuer;
 private final OpenLoginThrottle throttle;
 public OpenAuthenticationService(ApplicationBootstrapMapper bootstrap,ApplicationAuthMapper apps,TokenBootstrapMapper tokenBootstrap,TokenMapper tokens,SecretCipher secrets,SignatureVerifier signatures,OpenTokenIssuer issuer,OpenLoginThrottle throttle){this.bootstrap=bootstrap;this.apps=apps;this.tokenBootstrap=tokenBootstrap;this.tokens=tokens;this.secrets=secrets;this.signatures=signatures;this.issuer=issuer;this.throttle=throttle;}
 public record Credentials(@NotNull @Pattern(regexp="[a-f0-9]{32}") String appId,@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String appSecret){}
 public record Resolved(ClientIdentity client,Long tokenId){}
 public OpenTokenIssuer.Ticket issue(Credentials input,RequestProof proof,String ip){
  String key=ip+":"+input.appId();throttle.check(key);
  var app=bootstrap.byAppId(input.appId());if(app==null||!app.getEnabled()){throttle.fail(key);throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);}
  String known=secrets.decrypt(app.getSecretCipher(),app.getAppId());if(!MessageDigest.isEqual(known.getBytes(StandardCharsets.UTF_8),input.appSecret().getBytes(StandardCharsets.UTF_8))){throttle.fail(key);throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);}
  throttle.success(key);
  return TenantScope.within(app.getTenantId(),()->issuer.issue(app.getId(),app.getCredentialVersion(),proof,known));
 }
 public Resolved authenticate(String raw,RequestProof proof){
  if(raw==null||!raw.matches("[A-Za-z0-9_-]{43}"))throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);
  var token=tokenBootstrap.byHash(OpenTokens.hash(raw));if(token==null||!token.getExpiresAt().isAfter(Instant.now()))throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);
  return TenantScope.within(token.getTenantId(),()->{
   var app=apps.selectById(token.getApplicationId());if(app==null||!app.getEnabled()||!app.getPortal().equals(token.getPortal())||app.getCredentialVersion().longValue()!=token.getCredentialVersion().longValue())throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);
   signatures.verify(app,proof,secrets.decrypt(app.getSecretCipher(),app.getAppId()));return new Resolved(new ClientIdentity(app.getId(),app.getAppId(),app.getName(),app.getTenantId(),app.getPortal()),token.getId());
  });
 }
 public void revoke(long id){tokens.deleteById(id);}
}
