package com.sma.admin.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.sma.admin.common.security.SmaPrincipal;

@TestConfiguration(proxyBeanMethods=false)
public class TenantMockMvcConfiguration {
    @Bean MockMvcBuilderCustomizer syntheticTenantHost() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(request -> {
            // Explicit Host from a security test always wins. No arbitrary tenant header or production bypass exists.
            var auth=TestSecurityContextHolder.getContext().getAuthentication();
            if(request.getHeader("Host")==null && auth!=null && auth.getPrincipal() instanceof SmaPrincipal actor) {
                request.addHeader("Host",TenantTestFixtures.host(actor));
                request.setServerName(TenantTestFixtures.host(actor));
            }
            return request;
        }));
    }
}
