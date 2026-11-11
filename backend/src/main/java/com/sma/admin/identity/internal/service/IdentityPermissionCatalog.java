package com.sma.admin.identity.internal.service;
import java.util.List;
import com.sma.admin.common.security.*;
import org.springframework.stereotype.Component;
/** Management capabilities; navigation is registered with the C8 UI. */
@Component
public class IdentityPermissionCatalog implements PermissionCatalog {
    @Override public List<PermissionSpec> permissions(){return List.of(
            new PermissionSpec("identity:realm:view",null,"identity.permission.realm-view","查看应用身份域","LOGIC",null,null,0,"admin",true),
            new PermissionSpec("identity:realm:manage",null,"identity.permission.realm-manage","管理应用身份域","LOGIC",null,null,1,"admin",true),
            new PermissionSpec("identity:client:view",null,"identity.permission.client-view","查看登录客户端","LOGIC",null,null,2,"admin",true),
            new PermissionSpec("identity:client:manage",null,"identity.permission.client-manage","管理登录客户端","LOGIC",null,null,3,"admin",true));}
}
