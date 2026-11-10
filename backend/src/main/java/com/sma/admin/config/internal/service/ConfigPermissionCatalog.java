package com.sma.admin.config.internal.service;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component
public class ConfigPermissionCatalog implements PermissionCatalog {
 public List<PermissionSpec> permissions() {
  return List.of(
   new PermissionSpec("config:settings",null,"config.menu.settings","系统配置","DIRECTORY",null,"Setting",30,"admin",true),
   new PermissionSpec("config:dictionary:view","config:settings","config.menu.dictionary","数据字典","MENU","/config/dictionaries",null,0,"admin",true),
   new PermissionSpec("config:dictionary:create","config:dictionary:view","config.action.create","新增","BUTTON",null,null,0,"admin",true),
   new PermissionSpec("config:dictionary:update","config:dictionary:view","config.action.update","编辑","BUTTON",null,null,0,"admin",true),
   new PermissionSpec("config:dictionary:delete","config:dictionary:view","config.action.delete","删除","BUTTON",null,null,0,"admin",true),
   new PermissionSpec("config:parameter:view","config:settings","config.menu.parameter","全局参数","MENU","/config/parameters",null,10,"admin",true),
   new PermissionSpec("config:parameter:create","config:parameter:view","config.action.create","新增","BUTTON",null,null,0,"admin",true),
   new PermissionSpec("config:parameter:update","config:parameter:view","config.action.update","编辑","BUTTON",null,null,0,"admin",true),
   new PermissionSpec("config:parameter:delete","config:parameter:view","config.action.delete","删除","BUTTON",null,null,0,"admin",true));
 }
}
