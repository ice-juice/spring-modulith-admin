package com.sma.admin.tenant.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component
public class TenantPermissionCatalog implements PermissionCatalog {
    private final TenantPlatformProperties properties;
    public TenantPermissionCatalog(TenantPlatformProperties properties) {this.properties=properties;}
    @Override public List<PermissionSpec> permissions() {
        var entries=new java.util.ArrayList<>(List.of(new PermissionSpec("tenant:settings",null,"tenant.menu.settings","租户配置","DIRECTORY",null,"Settings",31,"admin",true),
                new PermissionSpec("tenant:brand:view","tenant:settings","tenant.menu.brand","品牌与登录页","MENU","/tenant/brand",null,0,"admin",true),
                new PermissionSpec("tenant:brand:edit","tenant:brand:view","tenant.action.edit","编辑草稿","BUTTON",null,null,0,"admin",true),
                new PermissionSpec("tenant:brand:publish","tenant:brand:view","tenant.action.publish","发布品牌","BUTTON",null,null,1,"admin",true),
                new PermissionSpec("tenant:brand:rollback","tenant:brand:view","tenant.action.rollback","回退版本","BUTTON",null,null,2,"admin",true)));
        // Seed platform operation menus only in the real platform tenant. Every API still checks deployment identity.
        if(CurrentUser.tenantId()==properties.platformTenantId()) entries.addAll(List.of(
                new PermissionSpec("tenant:tenant:view","tenant:settings","tenant.menu.tenants","租户管理","MENU","/tenant/tenants",null,1,"admin",true),
                new PermissionSpec("tenant:tenant:create","tenant:tenant:view","tenant.action.create-tenant","登记租户","BUTTON",null,null,0,"admin",true),
                new PermissionSpec("tenant:tenant:update","tenant:tenant:view","tenant.action.update-tenant","修改租户状态","BUTTON",null,null,1,"admin",true),
                new PermissionSpec("tenant:domain:view","tenant:settings","tenant.menu.domains","域名管理","MENU","/tenant/domains",null,2,"admin",true),
                new PermissionSpec("tenant:domain:create","tenant:domain:view","tenant.action.create-domain","登记域名","BUTTON",null,null,0,"admin",true),
                new PermissionSpec("tenant:domain:verify","tenant:domain:view","tenant.action.verify-domain","验证域名","BUTTON",null,null,1,"admin",true),
                new PermissionSpec("tenant:domain:update","tenant:domain:view","tenant.action.update-domain","管理域名状态","BUTTON",null,null,2,"admin",true)));
        return List.copyOf(entries);
    }
}
