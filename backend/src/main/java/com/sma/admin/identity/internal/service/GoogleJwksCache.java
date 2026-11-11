package com.sma.admin.identity.internal.service;

import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.stereotype.Component;

/** Provider-public keys only. Bounded node-local security state; no user/tenant claims are cached. */
@Component
public class GoogleJwksCache implements CacheCatalog {
    private record Snapshot(Map<String,RSAPublicKey> keys,Instant expiresAt) {}
    private volatile Snapshot snapshot=new Snapshot(Map.of(),Instant.EPOCH);
    private Instant nextAttempt=Instant.EPOCH;
    private final ReentrantLock refreshing=new ReentrantLock();
    private final GoogleHttpTransport http;private final IdentityClock clock;
    public GoogleJwksCache(GoogleHttpTransport http,IdentityClock clock){this.http=http;this.clock=clock;}
    public RSAPublicKey key(String kid){
        if(kid==null || !kid.matches("[A-Za-z0-9_-]{1,128}"))throw invalid();
        var now=clock.now();var current=snapshot;var known=current.keys().get(kid);
        if(known!=null && current.expiresAt().isAfter(now))return known;
        if(!refreshing.tryLock())throw unavailable();
        try{
            now=clock.now();current=snapshot;known=current.keys().get(kid);
            if(known!=null && current.expiresAt().isAfter(now))return known;
            if(now.isBefore(nextAttempt)){
                if(current.expiresAt().isAfter(now))throw invalid();
                throw unavailable();
            }
            nextAttempt=now.plusSeconds(30);
            var document=http.fetchKeys();
            try{
                if(document.body().length()>65536 || document.lifetime().isNegative())throw unavailable();
                var entries=JWKSet.parse(document.body()).getKeys();
                if(entries.isEmpty() || entries.size()>16)throw unavailable();
                Map<String,RSAPublicKey> keys=new HashMap<>();
                for(var entry:entries){
                    if(!(entry instanceof RSAKey rsa) || rsa.isPrivate() || rsa.getKeyID()==null || !rsa.getKeyID().matches("[A-Za-z0-9_-]{1,128}")
                            || rsa.getKeyUse()!=null && !KeyUse.SIGNATURE.equals(rsa.getKeyUse())
                            || rsa.getAlgorithm()!=null && !JWSAlgorithm.RS256.equals(rsa.getAlgorithm())
                            || rsa.getKeyOperations()!=null && !rsa.getKeyOperations().contains(KeyOperation.VERIFY))throw unavailable();
                    var key=rsa.toRSAPublicKey();int bits=key.getModulus().bitLength();
                    if(bits<2048 || bits>8192 || keys.putIfAbsent(rsa.getKeyID(),key)!=null)throw unavailable();
                }
                Duration lifetime=document.lifetime().compareTo(Duration.ofHours(6))>0?Duration.ofHours(6):document.lifetime();
                snapshot=new Snapshot(Map.copyOf(keys),now.plus(lifetime));
                var result=keys.get(kid);if(result==null)throw invalid();return result;
            }catch(BizException failure){throw failure;}
            catch(Exception malformed){throw unavailable();}
        }finally{refreshing.unlock();}
    }
    @Override public Collection<CacheDescriptor> regions(){return List.of(new CacheDescriptor("identity.google-jwks","identity","identity.cache.google-jwks",
            "authentication","google-keys",CacheDescriptor.Kind.PROTECTED,CacheDescriptor.Scope.NODE,1,
            new CachePolicy(Duration.ofHours(6),Duration.ZERO,16,65536,65536),Set.of(),Set.of("provider-max-age","bounded-unknown-key-refresh"),"identity:client:view"));}
    private BizException unavailable(){return new BizException(IdentityErrorCode.PROVIDER_UNAVAILABLE);}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);}
}
