package com.sma.admin.identity.internal.service;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.AppUserPrincipal;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IdentityPasswordReauthenticationCoordinator {
    public record Request(AppUserPrincipal actor,IdentityReauthenticationStore.Action action,String target,String password,String origin,String sourceAddress){
        @Override public String toString(){return "PasswordReauthentication[REDACTED]";}
    }
    private final IdentityPasswordReauthenticationStore store;private final IdentityPasswordWork passwords;
    public IdentityPasswordReauthenticationCoordinator(IdentityPasswordReauthenticationStore store,IdentityPasswordWork passwords){this.store=store;this.passwords=passwords;}
    public IdentityReauthenticationStore.Issued verify(Request input){
        IdentityAuthority.worker();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Reauthentication requires no outer transaction");
        if(input==null)throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
        var prepared=store.prepare(new IdentityPasswordReauthenticationStore.Attempt(input.actor(),input.action(),input.target(),input.origin(),input.sourceAddress()));
        if(prepared.error()!=null)throw new BizException(prepared.error());boolean verified=passwords.matches(input.password(),prepared.passwordHash());
        if(prepared.credentialId()==null || !verified)throw new BizException(IdentityErrorCode.INVALID_PASSWORD_LOGIN);return store.finish(prepared);
    }
}
