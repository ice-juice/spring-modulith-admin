package com.sma.admin.identity.internal.service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.IdentityClientRevisionEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GoogleProtocol {
    private final GoogleHttpTransport http;private final GoogleIdTokenVerifier tokens;private final TenantSecretCodec secrets;private final JsonMapper json;
    public GoogleProtocol(GoogleHttpTransport http,GoogleIdTokenVerifier tokens,TenantSecretCodec secrets,JsonMapper json){this.http=http;this.tokens=tokens;this.secrets=secrets;this.json=json;}
    public URI authorization(IdentityClientRevisionEntity configuration,String callback,String state,String nonce,String challenge){
        return authorization(configuration,callback,state,nonce,challenge,false);
    }
    public URI authorization(IdentityClientRevisionEntity configuration,String callback,String state,String nonce,String challenge,boolean selectAccount){
        validate(configuration,callback);proof(state);proof(nonce);proof(challenge);
        var fields=new LinkedHashMap<String,String>();fields.put("response_type","code");fields.put("scope","openid email profile");
        fields.put("client_id",configuration.getGoogleClientId());fields.put("redirect_uri",callback);fields.put("state",state);fields.put("nonce",nonce);
        fields.put("code_challenge",challenge);fields.put("code_challenge_method","S256");
        if(selectAccount)fields.put("prompt","select_account");
        return URI.create("https://accounts.google.com/o/oauth2/v2/auth?"+form(fields));
    }
    public GoogleIdTokenVerifier.Verified exchange(IdentityClientRevisionEntity configuration,String callback,String code,String verifier,String nonce){
        validate(configuration,callback);proof(nonce);
        if(code==null || code.isBlank() || code.length()>4096 || code.chars().anyMatch(Character::isISOControl))throw invalid();
        challenge(verifier); // Enforce RFC 7636 length/alphabet; the coordinator binds this proof to its saved S256 value.
        String raw;
        if(configuration.getSecretEnvelope()==null)raw=http.exchange(configuration.getGoogleClientId(),null,code,verifier,callback);
        else {
            var reference=new TenantConfigurationRef(configuration.getTenantId(),"identity",configuration.getClientId(),configuration.getConfigurationVersion(),configuration.getCredentialVersion());
            try(var value=secrets.decrypt(reference,"google-client-secret",configuration.getSecretEnvelope())){
                char[] chars=value.copy();try{raw=http.exchange(configuration.getGoogleClientId(),chars,code,verifier,callback);}finally{Arrays.fill(chars,'\0');}
            }
        }
        return tokens.verify(raw,configuration.getGoogleClientId(),configuration.getGoogleAuthorizedParty(),nonce);
    }
    public static String challenge(String verifier){
        if(verifier==null || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))throw new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void validate(IdentityClientRevisionEntity configuration,String callback){
        ActorContext.requireTenant(configuration.getTenantId());
        if(!configuration.getGoogleEnabled() || configuration.getGoogleClientId()==null
                || !json.readValue(configuration.getCallbacksJson(),new TypeReference<List<String>>(){}).contains(callback))throw invalid();
    }
    private void proof(String value){if(value==null || !value.matches("[A-Za-z0-9_-]{43}"))throw invalid();}
    private String form(Map<String,String> fields){return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);}
}
