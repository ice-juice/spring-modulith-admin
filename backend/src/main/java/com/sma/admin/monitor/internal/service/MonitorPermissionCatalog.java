package com.sma.admin.monitor.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component public class MonitorPermissionCatalog implements PermissionCatalog {
 public List<PermissionSpec> permissions(){return List.of(
  new PermissionSpec("monitor:center",null,"monitor.menu.center","系统监控","DIRECTORY",null,"Monitor",70,"admin",true),
  new PermissionSpec("monitor:server:view","monitor:center","monitor.menu.server","服务器监控","MENU","/monitor/server",null,0,"admin",true),
  new PermissionSpec("monitor:cache:view","monitor:center","monitor.menu.caches","缓存管理","MENU","/monitor/caches",null,10,"admin",true),
  new PermissionSpec("monitor:cache:clear","monitor:cache:view","monitor.action.clear","清理缓存","BUTTON",null,null,0,"admin",true));}
}
