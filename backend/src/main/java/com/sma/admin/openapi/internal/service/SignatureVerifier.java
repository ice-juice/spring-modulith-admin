package com.sma.admin.openapi.internal.service;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.BizException;
import com.sma.admin.openapi.OpenapiErrorCode;
import com.sma.admin.openapi.internal.persistence.*;

@Service
public class SignatureVerifier {
 private final NonceMapper nonces;private final NonceMaintenance maintenance;public SignatureVerifier(NonceMapper nonces,NonceMaintenance maintenance){this.nonces=nonces;this.maintenance=maintenance;}
 @Transactional public void verify(ApplicationEntity app,RequestProof proof,String secret){
  // Receipt bridges always require a fresh, body-bound signature, including legacy unsigned applications.
  if(!app.getSignatureRequired() && !org.springframework.web.util.UriUtils.decode(proof.path(),StandardCharsets.UTF_8).startsWith("/open/mail/"))return;
  try{
   if(!app.getAppId().equals(proof.appId())||proof.timestamp()==null||!proof.timestamp().matches("[0-9]{1,12}")||proof.nonce()==null||!proof.nonce().matches("[A-Za-z0-9_-]{16,64}")||proof.signature()==null||!proof.signature().matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException();
   long timestamp=Long.parseLong(proof.timestamp());if(Math.abs(Instant.now().getEpochSecond()-timestamp)>300)throw new IllegalArgumentException();
   String canonical=proof.method().toUpperCase(Locale.ROOT)+"\n"+proof.path()+"\n"+canonicalQuery(proof.query())+"\n"+proof.timestamp()+"\n"+proof.nonce()+"\n"+proof.bodyHash();
   var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   if(!MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)),HexFormat.of().parseHex(proof.signature())))throw new IllegalArgumentException();
  }catch(GeneralSecurityException|IllegalArgumentException invalid){throw new BizException(OpenapiErrorCode.INVALID_SIGNATURE);}
  var nonce=new NonceEntity();nonce.setApplicationId(app.getId());nonce.setNonce(proof.nonce());nonce.setExpiresAt(Instant.now().plusSeconds(600));
  try{nonces.insert(nonce);maintenance.seen();}catch(DuplicateKeyException replay){throw new BizException(OpenapiErrorCode.REPLAY);}
 }
 public static String canonicalQuery(String query){
  if(query==null||query.isEmpty())return "";
  record Pair(String key,String value){}
  List<Pair> pairs=new ArrayList<>();for(String item:query.split("&",-1)){if(item.isEmpty())continue;String[] pair=item.split("=",2);pairs.add(new Pair(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair.length>1?pair[1]:"",StandardCharsets.UTF_8)));}
  return pairs.stream().map(pair->new Pair(encode(pair.key()),encode(pair.value()))).sorted(Comparator.comparing(Pair::key).thenComparing(Pair::value)).map(pair->pair.key()+"="+pair.value()).collect(java.util.stream.Collectors.joining("&"));
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20").replace("*","%2A").replace("%7E","~");}
}
