package com.sma.admin.defense.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component
public class DefensePermissionCatalog implements PermissionCatalog {
    @Override public List<PermissionSpec> permissions(){return List.of(
        new PermissionSpec("defense:event:view",null,"defense.permission.events","查看本租户安全事件","LOGIC",null,null,0,"admin",true),
        new PermissionSpec("defense:platform:view",null,"defense.permission.platform","查看平台安全事件","LOGIC",null,null,1,"admin",false));}
}
