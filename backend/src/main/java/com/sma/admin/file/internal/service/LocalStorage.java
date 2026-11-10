package com.sma.admin.file.internal.service;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class LocalStorage implements StorageAdapter {
 private final Path root;
 public LocalStorage(FileProperties properties)throws IOException {Path directory=Path.of(properties.directory()).toAbsolutePath().normalize();Files.createDirectories(directory);root=directory.toRealPath();}
 public static class TooLarge extends IOException {}
 @Override public Stored put(InputStream input,long maxBytes)throws IOException {
  String key=UUID.randomUUID()+".blob";Path target=path(key);boolean created=false;
  try{
   MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
   try(var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
    created=true;byte[] buffer=new byte[8192];int count;
    while((count=input.read(buffer))!=-1){size+=count;if(size>maxBytes)throw new TooLarge();digest.update(buffer,0,count);output.write(buffer,0,count);}
   }
   return new Stored(key,size,HexFormat.of().formatHex(digest.digest()));
  }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  catch(IOException|RuntimeException failure){if(created)Files.deleteIfExists(target);throw failure;}
 }
 @Override public Path path(String key) {
  if(key==null||!key.matches("[a-f0-9-]{36}\\.blob"))throw new IllegalArgumentException("Invalid object key");
  Path target=root.resolve(key).normalize();if(!target.startsWith(root)||Files.isSymbolicLink(target))throw new IllegalArgumentException("Unsafe object path");return target;
 }
 @Override public void remove(String key)throws IOException {Files.deleteIfExists(path(key));}
}
