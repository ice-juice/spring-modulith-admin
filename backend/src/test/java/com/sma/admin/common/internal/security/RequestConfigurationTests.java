package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class RequestConfigurationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RequestGovernanceProperties.class)
    static class BindingConfiguration {}

    @Test void overloadedRecordConstructorStillBindsAsAnApplicationBean() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("sma.security.requests.max-concurrent=3", "sma.security.requests.minimum-write-interval=750ms")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RequestGovernanceProperties.class);
                    var properties=context.getBean(RequestGovernanceProperties.class);
                    assertThat(properties.maxConcurrent()).isEqualTo(3);
                    assertThat(properties.minimumWriteInterval()).isEqualTo(java.time.Duration.ofMillis(750));
                    assertThat(properties.maxRecordsPerTenant()).isEqualTo(100000L);
                });
    }

    @Test void documentedEnvironmentNamesBindWithoutExposingCredentials() throws Exception {
        var environment = new StandardEnvironment();
        String key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
        environment.getPropertySources().addFirst(new MapPropertySource("test-environment", Map.of(
                "SMA_SECURITY_REQUESTS_FINGERPRINT_KEY", key,
                "SMA_SECURITY_REQUESTS_FINGERPRINT_KEY_ID", "next",
                "SMA_SECURITY_REQUESTS_MAX_CONCURRENT", "4",
                "SMA_SECURITY_BROWSER_ALLOWED_ORIGINS", "https://admin.example.com")));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        var properties = Binder.get(environment).bind("sma.security.requests", Bindable.of(RequestGovernanceProperties.class)).get();
        assertThat(properties.fingerprintKey()).isEqualTo(key);
        assertThat(properties.fingerprintKeyId()).isEqualTo("next"); assertThat(properties.maxConcurrent()).isEqualTo(4);
        assertThat(properties.toString()).doesNotContain(key);
        assertThat(Binder.get(environment).bind("sma.security.browser", Bindable.of(BrowserRequestProperties.class)).get().allowedOrigins())
                .containsExactly("https://admin.example.com");
    }
}
