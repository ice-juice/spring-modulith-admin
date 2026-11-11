package com.sma.admin.auth.internal.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sma.admin.auth.internal.persistence.SessionEntity;
import com.sma.admin.iam.AccountQuery;

/** One predicate for REST, WebSocket, security settings and unlocking. */
@Service
public class SessionValidity {
    private final AccountQuery accounts;
    private final AccountSecurityStore security;
    private final TenantAuthenticationGuard tenants;
    public SessionValidity(AccountQuery accounts,AccountSecurityStore security,TenantAuthenticationGuard tenants){this.accounts=accounts;this.security=security;this.tenants=tenants;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.READ_COMMITTED)
    public boolean valid(SessionEntity row, boolean allowLocked) {
        if(row==null||!row.getExpiresAt().isAfter(Instant.now())||(!allowLocked&&Boolean.TRUE.equals(row.getLocked())))return false;
        if(!tenants.active(row.getTenantId(),row.getPortal(),row.getTenantSecurityGeneration()))return false;
        var user=accounts.findById(row.getUserId());
        if(user.isEmpty()||!user.get().enabled()||!user.get().portal().equals(row.getPortal())||user.get().credentialVersion()!=row.getCredentialVersion())return false;
        long version=security.version(row.getUserId(),row.getPortal());
        if(row.getSecurityVersion()==null||version!=row.getSecurityVersion())return false;
        var totp=security.totp(row.getUserId(),row.getPortal());
        return totp==null||!Boolean.TRUE.equals(totp.getEnabled())||("PASSWORD_TOTP".equals(row.getAuthenticationMethod())&&row.getMfaVerifiedAt()!=null&&row.getPasswordVerifiedAt()!=null);
    }
}
