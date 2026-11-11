package com.sma.admin.openapi.internal.service;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.openapi.OpenapiErrorCode;
import com.sma.admin.openapi.internal.persistence.*;
/** Shared pre-authentication reservations; never refunded by successful or failed credential checks. */
@Component
public class OpenLoginThrottle {
 private static final String KEY_CONTEXT="openapi:login-budget-key:v1";
 private static final Duration WINDOW=Duration.ofMinutes(5);
 private final LoginBudgetMapper budgets;private final LoginBudgetGuardMapper guards;private final SecretCipher secrets;
 private final TransactionTemplate reads,writes;
 public OpenLoginThrottle(LoginBudgetMapper budgets,LoginBudgetGuardMapper guards,SecretCipher secrets,PlatformTransactionManager transactions){
  this.budgets=budgets;this.guards=guards;this.secrets=secrets;
  reads=transaction(transactions,true);writes=transaction(transactions,false);
 }
 private static TransactionTemplate transaction(PlatformTransactionManager manager,boolean readOnly){
  var template=new TransactionTemplate(manager);template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);template.setReadOnly(readOnly);template.setTimeout(5);return template;
 }
 public void check(String source,String appId){
  if(source==null||source.isBlank()||source.length()>128||source.chars().anyMatch(Character::isISOControl)||appId==null||!appId.matches("[a-f0-9]{32}"))
   throw new BizException(OpenapiErrorCode.INVALID_CREDENTIALS);
  var material=material();
  // Two separate commits: a rejected pair must not refund the already reserved source budget.
  claim(material,"SOURCE",source,60);claim(material,"PAIR",source+"\n"+appId,10);
 }
 private record Material(String id,SecretKeySpec key){@Override public String toString(){return "Material[REDACTED]";}}
 private Material material(){
  var guard=reads.execute(status->guards.selectById(1L));
  if(guard==null){
   // Encryption/file-key access is outside our budget transactions; races retain the first committed key.
   String cipher=secrets.encrypt(secrets.randomSecret(),KEY_CONTEXT),id=UUID.randomUUID().toString();
   writes.execute(status->{
    if(budgets.selectCount(null)>0&&guards.selectById(1L)==null)throw new BizException(OpenapiErrorCode.SECRET_STORAGE);
    return guards.ensure(id,cipher);
   });guard=reads.execute(status->guards.selectById(1L));
  }
  if(guard==null)throw new BizException(OpenapiErrorCode.SECRET_STORAGE);
  byte[] bytes;
  try{bytes=Base64.getUrlDecoder().decode(secrets.decrypt(guard.getKeyCipher(),KEY_CONTEXT));}
  catch(IllegalArgumentException invalid){throw new BizException(OpenapiErrorCode.SECRET_STORAGE);}
  try{if(bytes.length!=32)throw new BizException(OpenapiErrorCode.SECRET_STORAGE);return new Material(guard.getKeyId(),new SecretKeySpec(bytes,"HmacSHA256"));}
  finally{Arrays.fill(bytes,(byte)0);}
 }
 private void claim(Material material,String scope,String subject,int limit){
  String hash=digest(material,scope,subject);
  boolean accepted=Boolean.TRUE.equals(writes.execute(status->{
   var guard=guards.selectOne(new LambdaQueryWrapper<LoginBudgetGuardEntity>().eq(LoginBudgetGuardEntity::getId,1L).last("FOR UPDATE"));
   if(guard==null||!guard.getKeyId().equals(material.id()))throw new BizException(OpenapiErrorCode.SECRET_STORAGE);
   budgets.removeExpired();var now=budgets.now();
   var row=budgets.selectOne(new LambdaQueryWrapper<LoginBudgetEntity>().eq(LoginBudgetEntity::getScope,scope).eq(LoginBudgetEntity::getSubjectHash,hash));
   if(row==null){
    if(budgets.selectCount(null)>=10000)return false;
    if(budgets.create(IdWorker.getId(),scope,hash,now.plus(WINDOW))!=1)throw new IllegalStateException("Login budget creation unavailable");return true;
   }
   boolean expired=!row.getExpiresAt().isAfter(now);int count=expired?0:row.getAttemptCount();
   if(count>=limit)return false;
   if(budgets.reserve(row.getId(),count+1,expired?now.plus(WINDOW):row.getExpiresAt())!=1)throw new IllegalStateException("Login budget reservation unavailable");
   return true;
  }));
  if(!accepted)throw new BizException(OpenapiErrorCode.RATE_LIMIT);
 }
 private String digest(Material material,String scope,String subject){
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(material.key());mac.update(("sma:openapi-budget:v1\n"+scope+"\n").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.GeneralSecurityException failure){throw new BizException(OpenapiErrorCode.SECRET_STORAGE);}
 }
}
