package com.sma.admin.openapi.internal.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.openapi.OpenapiErrorCode;

@Component @EnableConfigurationProperties(OpenapiProperties.class)
public class SecretCipher implements com.sma.admin.common.deployment.DeploymentEvidenceSource {
 private final OpenapiProperties properties;private final SecureRandom random=new SecureRandom();private SecretKeySpec key;private boolean generatedInThisProcess;
 public SecretCipher(OpenapiProperties properties){this.properties=properties;}
 public String randomSecret(){byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
 private SecretKeySpec key()throws IOException{return key(true);}
 private synchronized SecretKeySpec key(boolean allowGenerate)throws IOException{
  if(key!=null)return key;byte[] bytes;
  if(properties.masterKey()!=null&&!properties.masterKey().isBlank()){
   try{bytes=Base64.getDecoder().decode(properties.masterKey());}catch(IllegalArgumentException invalid){bytes=Base64.getUrlDecoder().decode(properties.masterKey());}
  }else{
   Path path=Path.of(properties.keyFile()).toAbsolutePath().normalize();if(allowGenerate)Files.createDirectories(path.getParent());if(Files.isSymbolicLink(path))throw new IOException("Unsafe key file");
   if(!Files.exists(path)){if(!allowGenerate)throw new IOException("Missing deployment key");byte[] generated=new byte[32];random.nextBytes(generated);try{Files.write(path,generated,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);generatedInThisProcess=true;
     if(path.getFileSystem().supportedFileAttributeViews().contains("posix"))Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }catch(FileAlreadyExistsException concurrent){/* Read the key created by the other process. */}}
   if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=32)throw new IOException("Invalid deployment key file");
   bytes=Files.readAllBytes(path);
  }
  if(bytes.length!=32)throw new IOException("Master key must contain 32 bytes");key=new SecretKeySpec(bytes,"AES");Arrays.fill(bytes,(byte)0);return key;
 }
 @Override public String id(){return "openapi.master-key";}
 @Override public synchronized com.sma.admin.common.deployment.DeploymentEvidence.Proof collect(com.sma.admin.common.deployment.DeploymentEvidence.Challenge challenge){
  com.sma.admin.common.deployment.DeploymentEvidence.requireHost();byte[] material=null;
  try{
   if(generatedInThisProcess)return com.sma.admin.common.deployment.DeploymentEvidence.unavailable(id());
   material=key(false).getEncoded();
   return com.sma.admin.common.deployment.DeploymentEvidence.keyRing(id(),challenge,"master",Map.of("master",material));
  }catch(IOException|IllegalArgumentException unavailable){return com.sma.admin.common.deployment.DeploymentEvidence.unavailable(id());}
  finally{if(material!=null)Arrays.fill(material,(byte)0);}
 }
 public String encrypt(String secret,String appId){
  try{byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));cipher.updateAAD(appId.getBytes(StandardCharsets.UTF_8));
   byte[] encrypted=cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));byte[] value=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,value,0,iv.length);System.arraycopy(encrypted,0,value,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(value);
  }catch(GeneralSecurityException|IOException|IllegalArgumentException failure){throw new BizException(OpenapiErrorCode.SECRET_STORAGE);}
 }
 public String decrypt(String encrypted,String appId){
  try{byte[] value=Base64.getDecoder().decode(encrypted);if(value.length<28)throw new GeneralSecurityException();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(value,12)));cipher.updateAAD(appId.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Arrays.copyOfRange(value,12,value.length)),StandardCharsets.UTF_8);
  }catch(GeneralSecurityException|IOException|IllegalArgumentException failure){throw new BizException(OpenapiErrorCode.SECRET_STORAGE);}
 }
}
