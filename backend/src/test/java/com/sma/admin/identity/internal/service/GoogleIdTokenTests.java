package com.sma.admin.identity.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sma.admin.common.BizException;
import org.junit.jupiter.api.*;

@Timeout(20)
class GoogleIdTokenTests {
    private static RSAKey key,other;
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");
    private final String audience="123456-shop.apps.googleusercontent.com",nonce="n".repeat(43);
    @BeforeAll static void keys()throws Exception{key=new RSAKeyGenerator(2048).keyID("key-a").generate();other=new RSAKeyGenerator(2048).keyID("key-b").generate();}
    private JWTClaimsSet.Builder claims(){return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("CaseSensitiveSubject")
            .audience(audience).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).claim("nonce",nonce)
            .claim("email","person@example.test").claim("email_verified",true);}
    private String signed(JWTClaimsSet claims)throws Exception{return signed(key,new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-a").type(JOSEObjectType.JWT).build(),claims);}
    private String signed(RSAKey signer,JWSHeader header,JWTClaimsSet claims)throws Exception{var jwt=new SignedJWT(header,claims);jwt.sign(new RSASSASigner(signer));return jwt.serialize();}
    private GoogleHttpTransport.JwksDocument document(RSAKey key){return new GoogleHttpTransport.JwksDocument(new JWKSet(key.toPublicJWK()).toString(),Duration.ofSeconds(60));}
    private GoogleIdTokenVerifier verifier(){var http=mock(GoogleHttpTransport.class);when(http.fetchKeys()).thenReturn(document(key));
        var clock=mock(IdentityClock.class);when(clock.now()).thenReturn(now);return new GoogleIdTokenVerifier(new GoogleJwksCache(http,clock),clock);}
    @Test void officialIssuerAliasesCanonicalizeAndEmailNeverReplacesSubject()throws Exception{
        var verifier=verifier();var proof=verifier.verify(signed(claims().issuer("accounts.google.com").claim("azp",audience).build()),audience,nonce);
        assertThat(proof.issuer()).isEqualTo("https://accounts.google.com");assertThat(proof.subject()).isEqualTo("CaseSensitiveSubject");assertThat(proof.emailVerified()).isTrue();
        var unverified=verifier.verify(signed(claims().claim("email_verified",false).build()),audience,nonce);
        assertThat(unverified.emailVerified()).isFalse();assertThat(unverified.subject()).isEqualTo(proof.subject());
    }
    @Test void wrongIssuerAudienceAuthorizedPartyNonceTimeAndSubjectAreRejected()throws Exception{
        var verifier=verifier();
        var invalid=List.of(claims().issuer("https://evil.example").build(),claims().audience("other-client").build(),
                claims().audience(List.of(audience,"other-client")).build(),claims().claim("azp","other-client").build(),
                claims().claim("nonce","other").build(),claims().claim("nonce",null).build(),claims().expirationTime(null).build(),
                claims().expirationTime(Date.from(now.minusSeconds(61))).build(),claims().issueTime(Date.from(now.plusSeconds(61))).build(),
                claims().notBeforeTime(Date.from(now.plusSeconds(61))).build(),claims().expirationTime(Date.from(now.plusSeconds(7201))).build(),
                claims().issueTime(Date.from(now.minusSeconds(7201))).build(),claims().subject(null).build());
        for(var c:invalid){String raw=signed(c);assertThatThrownBy(()->verifier.verify(raw,audience,nonce)).isInstanceOf(BizException.class);}
        String valid=signed(claims().build());assertThatThrownBy(()->verifier.verify(valid,"another.apps.googleusercontent.com",nonce)).isInstanceOf(BizException.class);
    }
    @Test void forgedSignatureAndHeaderProvidedKeyLocationsCannotBeUsed()throws Exception{
        var verifier=verifier();String forged=signed(other,new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-a").build(),claims().build());
        assertThatThrownBy(()->verifier.verify(forged,audience,nonce)).isInstanceOf(BizException.class);
        for(var header:List.of(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-a").jwkURL(URI.create("https://evil.example/keys")).build(),
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-a").jwk(other.toPublicJWK()).build(),
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key-a").x509CertURL(URI.create("https://evil.example/cert")).build())){
            String raw=signed(key,header,claims().build());assertThatThrownBy(()->verifier.verify(raw,audience,nonce)).isInstanceOf(BizException.class);
        }
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims().build());jwt.sign(new MACSigner(new byte[32]));
        assertThatThrownBy(()->verifier.verify(jwt.serialize(),audience,nonce)).isInstanceOf(BizException.class);
    }
    @Test void hybridNativePresenterRequiresTheExplicitAuthorizedPartyAndNeverAWiderClientSet()throws Exception{
        var verifier=verifier();String presenter="123456-android.apps.googleusercontent.com";
        String nativeToken=signed(claims().claim("azp",presenter).build());
        assertThat(verifier.verify(nativeToken,audience,presenter,nonce).subject()).isEqualTo("CaseSensitiveSubject");
        assertThatThrownBy(()->verifier.verify(nativeToken,audience,nonce)).isInstanceOf(BizException.class);
        String missing=signed(claims().build()),web=signed(claims().claim("azp",audience).build());
        assertThatThrownBy(()->verifier.verify(missing,audience,presenter,nonce)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->verifier.verify(web,audience,presenter,nonce)).isInstanceOf(BizException.class);
    }
    @Test void unknownKidRefreshIsBoundedAndExpiredKeysNeverSurviveProviderFailure(){
        var http=mock(GoogleHttpTransport.class);var clock=mock(IdentityClock.class);when(clock.now()).thenReturn(now);
        when(http.fetchKeys()).thenReturn(document(key),document(other)).thenThrow(new BizException(com.sma.admin.identity.IdentityErrorCode.PROVIDER_UNAVAILABLE));
        var cache=new GoogleJwksCache(http,clock);assertThat(cache.key("key-a")).isNotNull();
        for(int i=0;i<20;i++)assertThatThrownBy(()->cache.key("unknown")).isInstanceOf(BizException.class);
        verify(http,times(1)).fetchKeys();when(clock.now()).thenReturn(now.plusSeconds(31));assertThat(cache.key("key-b")).isNotNull();
        assertThatThrownBy(()->cache.key("key-a")).isInstanceOf(BizException.class);
        when(clock.now()).thenReturn(now.plusSeconds(92));assertThatThrownBy(()->cache.key("key-b")).isInstanceOf(BizException.class);verify(http,times(3)).fetchKeys();
        assertThat(cache.regions().iterator().next().operations()).isEmpty();
    }
    @Test void malformedPrivateDuplicateAndWeakKeysFailClosed()throws Exception{
        var weak=new RSAKeyGenerator(2048).keyID("weak").generate(); // Replace modulus with an insufficient public key, without signing anything with it.
        var weakPublic=new RSAKey.Builder(new com.nimbusds.jose.util.Base64URL("AQAB"),weak.getPublicExponent()).keyID("weak").build();
        for(String body:List.of("{}",new JWKSet(key).toString(false),new JWKSet(List.of(key.toPublicJWK(),key.toPublicJWK())).toString(),new JWKSet(weakPublic).toString())){
            var http=mock(GoogleHttpTransport.class);when(http.fetchKeys()).thenReturn(new GoogleHttpTransport.JwksDocument(body,Duration.ofMinutes(1)));
            var clock=mock(IdentityClock.class);when(clock.now()).thenReturn(now);var cache=new GoogleJwksCache(http,clock);
            assertThatThrownBy(()->cache.key("key-a")).isInstanceOf(BizException.class);
        }
    }
    @Test void concurrentKeyMissDoesNotQueueAnUnboundedRefreshWait()throws Exception{
        var http=mock(GoogleHttpTransport.class);var clock=mock(IdentityClock.class);when(clock.now()).thenReturn(now);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(http.fetchKeys()).thenAnswer(i->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException();return document(key);});
        var cache=new GoogleJwksCache(http,clock);
        try(var executor=Executors.newSingleThreadExecutor()){
            var first=executor.submit(()->cache.key("key-a"));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            try{assertThatThrownBy(()->cache.key("key-a")).isInstanceOf(BizException.class);}finally{release.countDown();}
            assertThat(first.get(3,TimeUnit.SECONDS)).isNotNull();verify(http,times(1)).fetchKeys();
        }
    }
}
