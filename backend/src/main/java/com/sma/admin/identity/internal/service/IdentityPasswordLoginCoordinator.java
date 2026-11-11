package com.sma.admin.identity.internal.service;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IdentityPasswordLoginCoordinator {
    public record Login(String loginHandle,String address,String password,String origin,String sourceAddress){@Override public String toString(){return "PasswordLogin[REDACTED]";}}
    private final IdentityPasswordLoginStore store;private final IdentityPasswordWork passwords;
    public IdentityPasswordLoginCoordinator(IdentityPasswordLoginStore store,IdentityPasswordWork passwords){this.store=store;this.passwords=passwords;}
    public IdentitySessionStore.Issued login(Login input){
        IdentityAuthority.worker();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Password login requires no outer transaction");
        if(input==null)throw new BizException(IdentityErrorCode.INVALID_PASSWORD_LOGIN);
        var prepared=store.prepare(new IdentityPasswordLoginStore.Attempt(input.loginHandle(),input.address(),input.origin(),input.sourceAddress()));
        if(prepared.error()!=null)throw new BizException(prepared.error());
        boolean verified=passwords.matches(input.password(),prepared.passwordHash());
        if(!prepared.found() || !verified)throw new BizException(IdentityErrorCode.INVALID_PASSWORD_LOGIN);
        return store.finish(prepared);
    }
}
