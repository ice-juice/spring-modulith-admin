package com.sma.admin.openapi.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component public class OpenapiPermissionCatalog implements PermissionCatalog{
 public List<PermissionSpec> permissions(){return List.of(
  new PermissionSpec("openapi:center",null,"openapi.menu.center","开放平台","DIRECTORY",null,"Connection",80,"admin",true),
  new PermissionSpec("openapi:app:view","openapi:center","openapi.menu.apps","应用中心","MENU","/openapi/apps",null,0,"admin",true),
  new PermissionSpec("openapi:app:create","openapi:app:view","openapi.action.create","创建应用","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("openapi:app:update","openapi:app:view","openapi.action.update","更新应用","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("openapi:app:delete","openapi:app:view","openapi.action.delete","删除应用","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("openapi:app:rotate","openapi:app:view","openapi.action.rotate","轮换密钥","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("openapi:app:link","openapi:app:view","openapi.action.link","关联对象","BUTTON",null,null,0,"admin",true));}
}
