package com.sma.admin.identity.internal.service;

import com.sma.admin.common.BizException;
import com.sma.admin.common.security.AppUserPrincipal;
import com.sma.admin.identity.IdentityErrorCode;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actor comes from the authenticated application security context, never from request JSON. */
@Service
public class IdentityGoogleOperations {
    private final IdentityLoginStore store;
    private final IdentityReauthenticationStore reauthentication;
    private final IdentityExternalLinks links;
    private final GoogleProtocol google;
    private final GoogleIdTokenVerifier verifier;

    public IdentityGoogleOperations(IdentityLoginStore store,IdentityReauthenticationStore reauthentication,
            IdentityExternalLinks links,GoogleProtocol google,GoogleIdTokenVerifier verifier){
        this.store=store;this.reauthentication=reauthentication;this.links=links;this.google=google;this.verifier=verifier;
    }
    @Transactional
    public IdentityLoginStore.Started beginReauthentication(IdentityLoginStore.Start input,AppUserPrincipal actor,
            IdentityReauthenticationStore.Action action,String target){
        return store.beginReauthentication(input,actor,action,target);
    }
    @Transactional
    public IdentityLoginStore.Started beginLink(IdentityLoginStore.Start input,AppUserPrincipal actor,IdentityReauthenticationStore.Use approval){
        if(approval==null || approval.action()!=IdentityReauthenticationStore.Action.GOOGLE_BINDING)
            throw new BizException(IdentityErrorCode.INVALID_REAUTHENTICATION);
        return reauthentication.consume(actor,approval,verified->store.beginLink(input,actor,verified));
    }
    public IdentityReauthenticationStore.Issued completeReauthentication(IdentityLoginStore.Completion input,AppUserPrincipal actor){
        return complete(input,actor,IdentityLoginStore.OperationKind.REAUTH,proof->operation->{
            links.requireOwned(actor,proof);
            return reauthentication.issue(actor,operation.actor(),operation.action(),operation.target(),IdentityReauthenticationStore.Method.GOOGLE);
        });
    }
    public IdentityExternalLinks.Linked completeLink(IdentityLoginStore.Completion input,AppUserPrincipal actor){
        return complete(input,actor,IdentityLoginStore.OperationKind.LINK_GOOGLE,
                proof->operation->links.add(actor,operation.actor(),proof));
    }
    private <T>T complete(IdentityLoginStore.Completion input,AppUserPrincipal actor,IdentityLoginStore.OperationKind kind,
            Function<GoogleIdTokenVerifier.Verified,Function<IdentityLoginStore.Operation,T>> mutation){
        IdentityAuthority.worker();
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Google operation requires no outer transaction");
        var claim=store.claimOperation(input,actor,kind);
        if(claim.error()!=null)throw new BizException(claim.error());
        var prepared=claim.prepared();
        try{
            var proof=prepared.flow()==IdentityLoginStore.Flow.CODE
                    ?google.exchange(prepared.configuration(),prepared.callbackUri(),input.credential(),input.verifier(),prepared.nonce())
                    :verifier.verify(input.credential(),prepared.configuration().getGoogleClientId(),prepared.configuration().getGoogleAuthorizedParty(),prepared.nonce());
            return store.finishOperation(prepared,actor,mutation.apply(proof));
        }catch(RuntimeException failure){
            try{store.fail(prepared);}catch(RuntimeException cleanupUnavailable){/* VERIFYING expires; provider work is never retried. */}
            throw failure;
        }
    }
}
