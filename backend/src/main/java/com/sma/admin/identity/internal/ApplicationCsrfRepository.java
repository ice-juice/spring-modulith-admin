package com.sma.admin.identity.internal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.*;

/** A distinct host-only cookie per client. Only a header is accepted as a request proof. */
final class ApplicationCsrfRepository implements CsrfTokenRepository {
    private final SecureRandom random=new SecureRandom();
    @Override public CsrfToken generateToken(HttpServletRequest request){byte[] value=new byte[32];random.nextBytes(value);return token(Base64.getUrlEncoder().withoutPadding().encodeToString(value));}
    @Override public CsrfToken loadToken(HttpServletRequest request){
        var client=ApplicationHttp.client(request);if(!client.web())return null;
        String value=ApplicationHttp.cookie(request,client,"csrf");return value!=null && value.matches("[A-Za-z0-9_-]{43}")?token(value):null;
    }
    @Override public void saveToken(CsrfToken token,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);if(!client.web())return;
        ApplicationHttp.cookie(response,client,"csrf",token==null?"":token.getToken(),false,token==null?Duration.ZERO:Duration.ofHours(1));
    }
    private CsrfToken token(String value){return new DefaultCsrfToken(ApplicationHttp.CSRF_HEADER,"_csrf",value);}
}
