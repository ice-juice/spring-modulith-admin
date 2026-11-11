package com.sma.admin.identity.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.common.security.AppUserPrincipal;
import com.sma.admin.identity.internal.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real application filter configuration with an isolated identity-store double, no database boot required. */
class ApplicationSecurityChainTests {
    private static final String HANDLE="h".repeat(43),CSRF="c".repeat(43),ACCESS="a".repeat(43),ORIGIN="https://app.example.test";
    @TestConfiguration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    static class Configuration {
        @Bean ApplicationClientResolver clients(){var resolver=mock(ApplicationClientResolver.class);
            when(resolver.resolve(HANDLE)).thenReturn(new ApplicationClientResolver.Client(new IdentityAuthority.Snapshot(1,2,3,"shop","WEB",1,1,1,1,1),HANDLE,"Shop",List.of(ORIGIN),"Lax",true,"client",true));return resolver;}
        @Bean IdentitySessionStore sessions()throws Exception{
            var sessions=mock(IdentitySessionStore.class);String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CSRF.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            when(sessions.authenticate(ACCESS,HANDLE,"WEB")).thenReturn(new IdentitySessionStore.Access(new AppUserPrincipal(4,1,2,3,"shop",5),"WEB",hash,HANDLE,
                    Instant.parse("2099-01-01T00:00:00Z"),Instant.parse("2099-01-02T00:00:00Z"),"PASSWORD",Instant.parse("2026-01-01T00:00:00Z")));return sessions;
        }
        @Bean ErrorResponseWriter errors(){return (request,response,error)->response.setStatus(error.status());}
        @Bean ProbeController probe(){return new ProbeController();}
    }
    @org.springframework.boot.test.context.TestComponent @RestController static class ProbeController {
        @GetMapping("/app/test-probe") public String get(){return "ok";}
        @PostMapping("/app/test-probe") public String post(){return "ok";}
    }
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder,String cookieToken,String headerToken){
        builder.secure(true).queryParam("appClient",HANDLE).header("Origin",ORIGIN)
                .cookie(new Cookie("__Host-sma-access-"+HANDLE,ACCESS),new Cookie("__Host-sma-csrf-"+HANDLE,cookieToken));
        if(headerToken!=null)builder.header("X-App-CSRF",headerToken);return builder;
    }
    @Test void restoringIdentityKeepsCsrfButStillRequiresCookieHeaderAndSessionProof()throws Exception{
        try(var context=new AnnotationConfigWebApplicationContext()){
            context.setServletContext(new MockServletContext());context.register(Configuration.class,ApplicationSecurityConfiguration.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters((Filter)context.getBean("springSecurityFilterChain")).build();
            for(var builder:List.of(get("/app/test-probe"),post("/app/test-probe"),post("/app/test-probe"))){
                var result=mvc.perform(request(builder,CSRF,CSRF)).andExpect(status().isOk()).andReturn();
                assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();assertThat(result.getRequest().getSession(false)).isNull();
            }
            mvc.perform(request(post("/app/test-probe"),CSRF,null)).andExpect(status().isForbidden());
            mvc.perform(request(post("/app/test-probe"),CSRF,"x".repeat(43))).andExpect(status().isForbidden());
            // A forged matching cookie/header pair passes double-submit but must still fail the server session binding.
            mvc.perform(request(post("/app/test-probe"),"x".repeat(43),"x".repeat(43))).andExpect(status().isForbidden());
        }
    }
}
