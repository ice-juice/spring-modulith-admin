package com.sma.admin.openapi.internal.service;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class OpenTokens {
 private static final SecureRandom RANDOM=new SecureRandom();private OpenTokens(){}
 static String create(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 static String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
 static String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
