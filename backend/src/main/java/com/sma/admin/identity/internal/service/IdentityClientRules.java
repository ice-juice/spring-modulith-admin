package com.sma.admin.identity.internal.service;

import java.net.URI;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.IdentityErrorCode;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class IdentityClientRules {
    private final Validator validator;
    public IdentityClientRules(Validator validator){this.validator=validator;}
    public void validate(IdentityClientService.Input input){
        if(input==null || !validator.validate(input).isEmpty())throw invalid();
        if(input.callbackUris().stream().anyMatch(uri->!callback(input.platform(),uri))
                || input.allowedOrigins().stream().anyMatch(uri->!origin(uri)))throw invalid();
        if(input.platform()==IdentityClientService.Platform.WEB && input.allowedOrigins().isEmpty())throw invalid();
        if(input.platform()!=IdentityClientService.Platform.WEB && (!input.allowedOrigins().isEmpty() || hasSecret(input)))throw invalid();
        if(input.platform()!=IdentityClientService.Platform.WEB && input.cookieSameSite()==IdentityClientService.CookieSameSite.NONE)throw invalid();
        if(input.googleClientId()!=null && !input.googleClientId().matches("[A-Za-z0-9_-]{3,220}\\.apps\\.googleusercontent\\.com"))throw invalid();
        if(input.googleAuthorizedParty()!=null && !input.googleAuthorizedParty().isEmpty()
                && (input.googleClientId()==null || !input.googleAuthorizedParty().matches("[A-Za-z0-9_-]{3,220}\\.apps\\.googleusercontent\\.com")))throw invalid();
        if(input.googleEnabled() && input.googleClientId()==null)throw invalid();
        if(hasSecret(input) && (input.googleClientId()==null || input.googleSecret().length<16))throw invalid();
        if(input.refreshTokenSeconds()<=input.accessTokenSeconds())throw invalid();
    }
    private boolean hasSecret(IdentityClientService.Input input){return input.googleSecret()!=null && input.googleSecret().length>0;}
    boolean origin(String value){
        try{var uri=URI.create(value);return https(uri) && (uri.getRawPath()==null || uri.getRawPath().isEmpty());}
        catch(RuntimeException invalid){return false;}
    }
    boolean callback(IdentityClientService.Platform platform,String value){
        try{
            if(value.length()>2048)return false;var uri=URI.create(value);
            if(uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getRawUserInfo()!=null)return false;
            String path=uri.getRawPath();
            if(path==null || !path.matches("/[A-Za-z0-9/_~.:-]*") || path.contains("..") || path.startsWith("//"))return false;
            if(https(uri))return true;
            return platform!=IdentityClientService.Platform.WEB && uri.getRawAuthority()==null && uri.getScheme()!=null
                    && uri.getScheme().matches("[a-z][a-z0-9-]*(?:\\.[a-z0-9-]+){2,}");
        }catch(RuntimeException invalid){return false;}
    }
    private boolean https(URI uri){
        String host=uri.getHost();
        return "https".equals(uri.getScheme()) && host!=null && host.equals(host.toLowerCase(java.util.Locale.ROOT))
                && host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}")
                && uri.getPort()==-1 && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null;
    }
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_CONFIGURATION);}
}
