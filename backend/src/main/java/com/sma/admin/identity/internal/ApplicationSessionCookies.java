package com.sma.admin.identity.internal;

import java.time.Duration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import com.sma.admin.identity.internal.service.*;

/** Shared response transport only; all authentication and session issuance remains in identity services. */
@Component
public class ApplicationSessionCookies {
    private final IdentityClock clock;
    public ApplicationSessionCookies(IdentityClock clock){this.clock=clock;}
    public void write(HttpServletResponse response,ApplicationClientResolver.Client client,IdentitySessionStore.Issued issued){
        if(!client.web())return;
        ApplicationHttp.cookie(response,client,"access",issued.accessToken(),true,Duration.between(clock.now(),issued.accessExpiresAt()));
        ApplicationHttp.cookie(response,client,"refresh",issued.refreshToken(),true,Duration.between(clock.now(),issued.refreshExpiresAt()));
        ApplicationHttp.cookie(response,client,"csrf",issued.csrfToken(),false,Duration.between(clock.now(),issued.refreshExpiresAt()));
    }
    public void clear(HttpServletResponse response,ApplicationClientResolver.Client client){
        if(client.web())for(String kind:java.util.List.of("access","refresh","csrf","login-proof","registration-proof","password-reset-proof","reauth-google-proof","link-google-proof","binding-proof"))ApplicationHttp.clear(response,client,kind);
    }
}
