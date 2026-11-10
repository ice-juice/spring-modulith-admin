package com.sma.admin.openapi.internal.service;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.openapi.OpenapiErrorCode;
import com.sma.admin.openapi.internal.persistence.*;
@Service
public class OpenTokenIssuer {
 private final ApplicationAuthMapper apps;private final TokenMapper tokens;private final SignatureVerifier signatures;
 public OpenTokenIssuer(ApplicationAuthMapper apps,TokenMapper tokens,SignatureVerifier signatures){this.apps=apps;this.tokens=tokens;this.signatures=signatures;}
 public record Ticket(String token,Instant expiresAt){}
 @Transactional public Ticket issue(long id,long version,RequestProof proof,String secret){
  var app=apps.selectOne(new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getId,id).last("FOR UPDATE"));
  if(app==null||!app.getEnabled()||app.getCredentialVersion()!=version)throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);
  signatures.verify(app,proof,secret);String raw=OpenTokens.create();var row=new TokenEntity();row.setApplicationId(app.getId());row.setPortal(app.getPortal());row.setCredentialVersion(app.getCredentialVersion());row.setTokenHash(OpenTokens.hash(raw));row.setExpiresAt(Instant.now().plusSeconds(3600));tokens.insert(row);return new Ticket(raw,row.getExpiresAt());
 }
}
