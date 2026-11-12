package com.sma.admin.tenant.internal.service;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.defense.DefensePlatformAccess;
import com.sma.admin.common.security.CurrentUser;
@Component
public class TenantDefensePlatformAccess implements DefensePlatformAccess {
    private final PlatformOperators operators;
    public TenantDefensePlatformAccess(PlatformOperators operators){this.operators=operators;}
    @Override public void requireViewer(){
        operators.require();
        if(!CurrentUser.require().permissions().contains("defense:platform:view"))throw new AccessDeniedException("Platform event access denied");
    }
}
