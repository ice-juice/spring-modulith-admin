package com.sma.admin.audit.internal.service;
import java.io.*;
import java.net.InetAddress;
import java.util.Objects;
import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class IpLocationService {
 private final Ip2Region lookup;
 public record Location(String country,String province,String city,String isp,String countryCode,boolean local,boolean resolved){}
 public IpLocationService() throws Exception {
  try(var v4=new ClassPathResource("ip2region/ip2region_v4.xdb").getInputStream();var v6=new ClassPathResource("ip2region/ip2region_v6.xdb").getInputStream()){
   lookup=Ip2Region.create(Config.custom().setCachePolicy(Config.BufferCache).setXdbInputStream(v4).asV4(),
    Config.custom().setCachePolicy(Config.BufferCache).setXdbInputStream(v6).asV6());
  }
 }
 public Location locate(String ip){
  try{
   if(ip==null||!ip.matches("[0-9a-fA-F:.]+"))return unknown();
   var address=InetAddress.getByName(ip);
   if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()
    ||(address.getAddress().length==16&&(address.getAddress()[0]&0xfe)==0xfc))return new Location(null,null,null,null,null,true,false);
   String region=lookup.search(address.getHostAddress());if(region==null||region.isBlank())return unknown();
   String[] values=region.split("\\|",-1);if(values.length<5)return unknown();
   return new Location(text(values[0]),text(values[1]),text(values[2]),text(values[3]),text(values[4]),false,text(values[4])!=null);
  }catch(Exception ex){return unknown();}
 }
 private String text(String value){return value==null||value.isBlank()||value.equals("0")?null:value;}
 private Location unknown(){return new Location(null,null,null,null,null,false,false);}
 @PreDestroy public void close() throws Exception {lookup.close();}
}
