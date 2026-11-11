package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.support.Users;

@IntegrationTest @AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="sma.security.browser.maximum-json-bytes=4096")
class BrowserSecurityIntegrationTests {
    @Autowired BrowserRequestProperties browserLimits;
    @Test void configuredJsonLimitRejectsBeforeParsingOrAuthentication(){
        assertThat(browserLimits.maximumJsonBytes()).isEqualTo(4096);
        assertThat(browserLimits.maximumConcurrentJson()).isEqualTo(16);
        assertThat(browserLimits.maximumConcurrentAnonymousJson()).isEqualTo(4);
        assertThat(mvc.post().uri("/api/auth/login").header("Host","localhost").header("Accept-Language","en-US")
                .contentType("application/json").content("x".repeat(4097))).hasStatus(413);
    }
    @Test void chunkedJsonLimitRemains413WhenMvcWrapsTheReadFailure(){
        assertThat(mvc.post().uri("/api/auth/login").header("Host","localhost").contentType("application/json")
                .content("{\"username\":\""+"x".repeat(4097)+"\"}")
                .with(request->new org.springframework.mock.web.MockHttpServletRequest(request.getServletContext()){
                    {setMethod(request.getMethod());setRequestURI(request.getRequestURI());setContentType(request.getContentType());setContent(request.getContentAsByteArray());addHeader("Host","localhost");}
                    @Override public long getContentLengthLong(){return -1;}
                    @Override public int getContentLength(){return -1;}
                })).hasStatus(413);
    }
    @Autowired MockMvcTester mvc;
    @Autowired ParameterService parameters;

    @Test void registeredFilterRejectsForgedWritesAndKeepsAuthenticationInCharge() {
        assertThat(mvc.post().uri("/api/iam/users").header("Origin", "https://evil.example")
                .contentType("application/json").content("{}")).hasStatus(403);
        assertThat(mvc.post().uri("/api/auth/login").contentType("text/plain").content("{}")
                .header("Accept-Language", "en-US")).hasStatus(415).bodyJson()
                .hasPathSatisfying("$.error", value -> value.assertThat().isEqualTo("common.error.unsupported-media-type"));
        assertThat(mvc.post().uri("/api/iam/users").header("Origin", "http://localhost")
                .contentType("application/json").content("{}")).hasStatus(401);
        assertThat(mvc.get().uri("/api/iam/users")).hasStatus(401);
    }

    @Test void boundSqlValuesRemainLiteralAndBrandKeysCannotBeWrittenThroughParameters() {
        Users.as(Users.principal(78101, 78101, DataAccess.unrestricted()), () -> {
            parameters.saveGroup(null, new ParameterService.GroupInput("securityprobe", "安全验证", null, 0, Map.of()));
            String suspicious = "%' OR 1=1 --";
            parameters.save(null, input("security.literal", suspicious, "normal"));
            parameters.save(null, input("security.other", "other", "normal"));
            assertThat(parameters.list("securityprobe", suspicious)).hasSize(1);
            assertThat(parameters.list("securityprobe", "UNION SELECT")).isEmpty();
            assertThatThrownBy(() -> parameters.save(null, input("system.copyrightUrl", "url", "javascript:alert(1)"))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> parameters.save(null, input("login.backgroundUrl", "background", "//evil.example/image"))).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> parameters.save(null, input("system.copyrightUrl", "url", "https://example.com/help?q=hello%20world"))).isInstanceOf(BizException.class);
            Users.as(Users.principal(78102, 78102, DataAccess.unrestricted()), () -> {
                assertThat(parameters.list("securityprobe", null)).isEmpty(); return null;
            });
            return null;
        });
    }
    @Test void brandValidationStillRejectsActiveLinksAndAcceptsSafeLegalLinks() {
        var validation=new com.sma.admin.tenant.internal.service.BrandValidation(new com.sma.admin.tenant.internal.service.LoginTemplates());
        var base=com.sma.admin.tenant.BrandContent.defaults("Test");
        for(String url:java.util.List.of("javascript:alert(1)","//evil.example/image")) {
            var invalid=new com.sma.admin.tenant.BrandContent(1,base.identity(),base.login(),
                    new com.sma.admin.tenant.BrandContent.Legal("Owner",null,url,"","","",""),base.links(),base.appearance(),Map.of(),Map.of());
            assertThatThrownBy(()->validation.validate(invalid)).isInstanceOf(BizException.class);
        }
        assertThat(validation.validate(new com.sma.admin.tenant.BrandContent(1,base.identity(),base.login(),
                new com.sma.admin.tenant.BrandContent.Legal("Owner",null,"https://example.com/help?q=hello%20world","","","",""),
                base.links(),base.appearance(),Map.of(),Map.of())).legal().copyrightUrl()).isEqualTo("https://example.com/help?q=hello%20world");
    }
    private ParameterService.Input input(String key, String name, String value) {
        return new ParameterService.Input(key, "securityprobe", name, null, "STRING", value, false, false, 0, Map.of());
    }
}
