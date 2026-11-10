package com.sma.admin.audit.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component public class AuditPermissionCatalog implements PermissionCatalog {
 public List<PermissionSpec> permissions(){return List.of(
  new PermissionSpec("audit:logs",null,"audit.menu.logs","审计日志","DIRECTORY",null,"Document",40,"admin",true),
  new PermissionSpec("audit:login:view","audit:logs","audit.menu.login","登录日志","MENU","/audit/logins",null,0,"admin",true),
  new PermissionSpec("audit:operation:view","audit:logs","audit.menu.operation","关键操作日志","MENU","/audit/operations",null,10,"admin",true));}
}
