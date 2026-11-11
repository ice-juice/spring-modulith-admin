package com.sma.admin.extension.internal.service;

import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.PermissionCatalog;
import com.sma.admin.common.security.PermissionSpec;

/** Navigation access only. Never grants any independent release-security privilege. */
@Component
public class ExtensionPermissionCatalog implements PermissionCatalog {
    public List<PermissionSpec> permissions(){return List.of(
        new PermissionSpec("extension:management",null,"extension.menu.management","业务扩展","DIRECTORY",null,"Package",85,"admin",true),
        new PermissionSpec("extension:console:view","extension:management","extension.menu.console","扩展管理","MENU","/extension/manage",null,0,"admin",false));}
}
