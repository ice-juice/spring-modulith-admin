package com.sma.admin.identity.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.stereotype.Component;

/** Cryptographic verification only. The login coordinator must additionally consume its persisted one-use transaction. */
@Component
public class GoogleIdTokenVerifier {
    public record Verified(String issuer,String subject,String email,boolean emailVerified) {}
    private final GoogleJwksCache keys;private final IdentityClock clock;
    public GoogleIdTokenVerifier(GoogleJwksCache keys,IdentityClock clock){this.keys=keys;this.clock=clock;}
    public Verified verify(String raw,String audience,String nonce){
        return verify(raw,audience,null,nonce);
    }
    public Verified verify(String raw,String audience,String authorizedParty,String nonce){
        if(raw==null || raw.length()>16384 || audience==null || audience.isBlank() || nonce==null || !nonce.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        try{
            var jwt=SignedJWT.parse(raw);var header=jwt.getHeader();
            if(!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getType()!=null && !JOSEObjectType.JWT.equals(header.getType())
                    || header.getCriticalParams()!=null && !header.getCriticalParams().isEmpty() || !header.isBase64URLEncodePayload()
                    || header.getJWKURL()!=null || header.getJWK()!=null || header.getX509CertURL()!=null)throw invalid();
            if(!jwt.verify(new RSASSAVerifier(keys.key(header.getKeyID()))))throw invalid();
            var claims=jwt.getJWTClaimsSet();var now=clock.now();
            if(!Set.of("accounts.google.com","https://accounts.google.com").contains(claims.getIssuer())
                    || !List.of(audience).equals(claims.getAudience()))throw invalid();
            String expectedParty=authorizedParty==null?audience:authorizedParty;
            String authorized=claims.getStringClaim("azp");
            if(authorized==null && !expectedParty.equals(audience) || authorized!=null && !expectedParty.equals(authorized))throw invalid();
            var numericDates=jwt.getPayload().toJSONObject();
            Instant expiry=instant(numericDates.get("exp")),issued=instant(numericDates.get("iat"));
            if(expiry==null || issued==null || !expiry.isAfter(now.minusSeconds(60)) || !expiry.isAfter(issued)
                    || issued.isAfter(now.plusSeconds(60)) || issued.isBefore(now.minusSeconds(7200)) || expiry.isAfter(issued.plusSeconds(7200)))throw invalid();
            Instant notBefore=instant(numericDates.get("nbf"));if(notBefore!=null && notBefore.isAfter(now.plusSeconds(60)))throw invalid();
            String actualNonce=claims.getStringClaim("nonce");
            if(actualNonce==null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),actualNonce.getBytes(StandardCharsets.UTF_8)))throw invalid();
            String subject=claims.getSubject();
            if(subject==null || !subject.matches("[!-~]{1,255}"))throw invalid();
            String email=claims.getStringClaim("email");boolean verified=Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"));
            if(email!=null && (email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
            // Identity is issuer + sub. A verified email is metadata and never an implicit account-merge instruction.
            return new Verified("https://accounts.google.com",subject,email,email!=null && verified);
        }catch(BizException failure){throw failure;}
        catch(Exception malformed){throw invalid();}
    }
    private Instant instant(Object value){
        if(value==null)return null;
        if(!(value instanceof Number number))throw invalid();
        return Instant.ofEpochSecond(new java.math.BigDecimal(number.toString()).longValueExact());
    }
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);}
}
