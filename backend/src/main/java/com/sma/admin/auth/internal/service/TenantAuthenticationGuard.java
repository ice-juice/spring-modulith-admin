package com.sma.admin.auth.internal.service;

import org.springframework.stereotype.Service;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;

/** Tenant state is authoritative database data, never a display-cache or a client-supplied tenant ID. */
@Service
public class TenantAuthenticationGuard {
    private final TenantEntryQuery tenants;
    public TenantAuthenticationGuard(TenantEntryQuery tenants) { this.tenants=tenants; }

    public long capture(long tenant,String portal) {
        var state=tenants.securityState(tenant).filter(TenantEntryQuery.SecurityState::acceptsLogin)
                .orElseThrow(() -> new BizException(AuthErrorCode.INVALID_CREDENTIALS));
        if(!matchesScope(tenant,portal,state.generation())) throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        return state.generation();
    }
    public boolean active(long tenant,String portal,Long generation) {
        if(generation==null || generation<1 || !matchesScope(tenant,portal,generation)) return false;
        return tenants.securityState(tenant).filter(state -> state.acceptsBusiness() && state.generation()==generation).isPresent();
    }
    public void require(long tenant,String portal,long generation) {
        if(!active(tenant,portal,generation)) throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
    }
    private boolean matchesScope(long tenant,String portal,long generation) {
        if(tenant!=CurrentUser.tenantId() || portal==null) return false;
        var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.isAuthenticated()) {
            if(authentication.getPrincipal() instanceof AppUserPrincipal) return false;
            if(authentication.getPrincipal() instanceof SmaPrincipal principal && (principal.machine() || !principal.portal().equals(portal))) return false;
        }
        return TenantEntryScope.optional().map(entry -> entry.tenantId()==tenant && entry.portal().equals(portal)
                && entry.securityGeneration()==generation).orElse(true);
    }
}
