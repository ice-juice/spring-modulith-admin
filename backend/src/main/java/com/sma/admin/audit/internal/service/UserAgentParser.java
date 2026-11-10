package com.sma.admin.audit.internal.service;
public final class UserAgentParser {
 private UserAgentParser(){}
 public record Device(String browser,String operatingSystem){}
 public static Device parse(String value){
  String agent=value==null?"":value;
  String browser=agent.contains("Edg/")?"Edge":agent.contains("OPR/")?"Opera":agent.contains("Chrome/")?"Chrome":
   agent.contains("Firefox/")?"Firefox":agent.contains("Safari/")?"Safari":"Unknown";
  String os=agent.contains("Android")?"Android":agent.contains("iPhone")||agent.contains("iPad")?"iOS":agent.contains("Windows")?"Windows":
   agent.contains("Mac OS")?"macOS":agent.contains("Linux")?"Linux":"Unknown";
  return new Device(browser,os);
 }
}
