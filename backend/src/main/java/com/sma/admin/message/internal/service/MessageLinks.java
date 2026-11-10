package com.sma.admin.message.internal.service;
import java.net.URI;
import com.sma.admin.common.*;
final class MessageLinks {
 private MessageLinks(){}
 static String validate(String value,boolean internal){
  if(value==null||value.isBlank())return null;
  if(value.indexOf('\\')>=0||value.chars().anyMatch(Character::isISOControl))throw new BizException(CommonErrorCode.BAD_REQUEST);
  try{
   URI uri=URI.create(value);
   if(internal&&value.startsWith("/")&&!value.startsWith("//")&&uri.getHost()==null)return value;
   if(!java.util.Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(java.util.Locale.ROOT))||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();
   return value;
  }catch(IllegalArgumentException failure){throw new BizException(CommonErrorCode.BAD_REQUEST);}
 }
}
