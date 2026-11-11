package com.sma.admin.identity.internal.service;

import com.sma.admin.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Neither external verification nor token exchange may share an outer database transaction. */
@Service
public class GoogleLoginCoordinator {
    private final IdentityLoginStore store;private final GoogleProtocol google;private final GoogleIdTokenVerifier verifier;
    public GoogleLoginCoordinator(IdentityLoginStore store,GoogleProtocol google,GoogleIdTokenVerifier verifier){this.store=store;this.google=google;this.verifier=verifier;}
    public IdentityLoginStore.Started begin(IdentityLoginStore.Start input){
        IdentityAuthority.worker();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Login coordinator requires no outer transaction");
        return store.begin(input);
    }
    public IdentitySessionStore.Issued complete(IdentityLoginStore.Completion input){
        IdentityAuthority.worker();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Login coordinator requires no outer transaction");
        var claim=store.claim(input);if(claim.error()!=null)throw new BizException(claim.error());var prepared=claim.prepared();
        try{
            var identity=prepared.flow()==IdentityLoginStore.Flow.CODE
                    ?google.exchange(prepared.configuration(),prepared.callbackUri(),input.credential(),input.verifier(),prepared.nonce())
                    :verifier.verify(input.credential(),prepared.configuration().getGoogleClientId(),prepared.configuration().getGoogleAuthorizedParty(),prepared.nonce());
            return store.finish(prepared,identity);
        }catch(RuntimeException failure){
            try{store.fail(prepared);}catch(RuntimeException cleanupUnavailable){/* Keep VERIFYING for bounded expiry recovery; never retry provider work. */}
            throw failure;
        }
    }
}
