package com.sma.admin.auth.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component
public class AuthPermissionCatalog implements PermissionCatalog {
    @Override public List<PermissionSpec> permissions() {
        return List.of(
            new PermissionSpec("auth:security",null,"auth.menu.security","安全管理","DIRECTORY",null,"Lock",20,"admin",true),
            new PermissionSpec("auth:account-security:view","auth:security","auth.menu.account-security","账号安全","MENU","/auth/security","Lock",10,"admin",true),
            new PermissionSpec("auth:session:view","auth:security","auth.menu.session","在线会话","MENU","/auth/sessions",null,0,"admin",true),
            new PermissionSpec("auth:session:revoke","auth:session:view","auth.action.revoke","强制下线","BUTTON",null,null,0,"admin",true));
    }
}
