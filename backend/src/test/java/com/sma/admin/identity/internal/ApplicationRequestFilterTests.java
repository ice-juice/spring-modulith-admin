package com.sma.admin.identity.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import jakarta.servlet.http.*;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.identity.internal.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class ApplicationRequestFilterTests {
    private final String handle="a".repeat(43),origin="https://app.example.test";
    private final ApplicationClientResolver resolver=mock(ApplicationClientResolver.class);
    private final ErrorResponseWriter errors=(request,response,error)->response.setStatus(error.status());
    private ApplicationRequestFilter filter(){
        var authority=new IdentityAuthority.Snapshot(1,2,3,"shop","WEB",1,1,1,1,1);
        when(resolver.resolve(handle)).thenReturn(new ApplicationClientResolver.Client(authority,handle,"Shop",List.of(origin),"Lax",true,"client",true));
        return new ApplicationRequestFilter(resolver,errors,new IdentityHttpProperties(1,4096));
    }
    private MockHttpServletRequest request(){
        var r=new MockHttpServletRequest("POST","/app/identity/google/transactions");r.setSecure(true);r.setScheme("https");r.addHeader("Origin",origin);
        r.setQueryString("appClient="+handle);r.setContentType("application/json");r.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;
    }
    @Test void encodedRouteAndUnknownContentLengthStillUseActualBoundedBytes()throws Exception{
        var f=filter();var r=request();r.setRequestURI("/app/identity/%67oogle/transactions");r.setContent(new byte[4097]);
        var unknown=new HttpServletRequestWrapper(r){@Override public long getContentLengthLong(){return -1;}@Override public int getContentLength(){return -1;}};
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();f.doFilter(unknown,response,chain);
        assertThat(response.getStatus()).isEqualTo(413);assertThat(chain.getRequest()).isNull();
    }
    @Test void duplicateCredentialHeadersCookiesAndEncodedSelectorCannotBeAmbiguous()throws Exception{
        var f=filter();
        for(int variant=0;variant<4;variant++){
            var r=request();switch(variant){
                case 0->r.addHeader("Origin",origin);
                case 1->{r.addHeader("X-App-CSRF","one");r.addHeader("X-App-CSRF","two");}
                case 2->r.setCookies(new Cookie("__Host-sma-csrf-"+handle,"one"),new Cookie("__Host-sma-csrf-"+handle,"two"));
                default->r.setQueryString("appClient="+handle+"&%61ppClient="+handle);
            }
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();f.doFilter(r,response,chain);
            assertThat(response.getStatus()).as("variant %s",variant).isEqualTo(400);assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void saturatedFilterDoesNotQueueAndAlwaysReleasesAfterDownstreamFailure()throws Exception{
        var f=filter();var rejected=new MockHttpServletResponse();
        assertThatThrownBy(()->f.doFilter(request(),new MockHttpServletResponse(),(req,res)->{
            f.doFilter(request(),rejected,new MockFilterChain());throw new jakarta.servlet.ServletException("test downstream failure");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(rejected.getStatus()).isEqualTo(503);
        var chain=new MockFilterChain();f.doFilter(request(),new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isNotNull();
    }
    @Test void safeSameOriginGetCanOmitOriginButCrossSiteOrUnsafeRequestCannot()throws Exception{
        var f=filter();var safe=request();safe.setMethod("GET");safe.setRequestURI("/app/identity/bootstrap");safe.removeHeader("Origin");safe.setServerName("app.example.test");safe.setServerPort(443);
        var chain=new MockFilterChain();f.doFilter(safe,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isNotNull();
        safe.addHeader("Sec-Fetch-Site","cross-site");var denied=new MockHttpServletResponse();f.doFilter(safe,denied,new MockFilterChain());assertThat(denied.getStatus()).isEqualTo(403);
        var unsafe=request();unsafe.removeHeader("Origin");unsafe.setServerName("app.example.test");unsafe.setServerPort(443);
        var deniedPost=new MockHttpServletResponse();f.doFilter(unsafe,deniedPost,new MockFilterChain());assertThat(deniedPost.getStatus()).isEqualTo(403);
    }
}
