package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;

class CacheConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfiguration.class)
            .withBean(CacheRevisionStore.class, () -> org.mockito.Mockito.mock(CacheRevisionStore.class))
            .withBean(CacheCatalog.class, com.sma.admin.config.internal.service.ConfigCacheCatalog::new);

    @Test void bindsTheDocumentedDottedRegionOverrideWithoutCreatingOtherRegions() {
        runner.withPropertyValues("sma.cache.regions[config.parameters].ttl=60s",
                "sma.cache.regions[config.parameters].negative-ttl=10s").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(CacheProperties.class);
            assertThat(properties.regions()).containsOnlyKeys("config.parameters");
            assertThat(properties.policy("config.parameters", CachePolicy.defaults()).ttl()).isEqualTo(Duration.ofSeconds(60));
            assertThat(context).hasSingleBean(ManagedCache.class);
        });
    }

    @Test void noneProviderIsBoundAndActuallyBypassesStorage() {
        runner.withPropertyValues("sma.cache.provider=none").run(context -> {
            assertThat(context).hasNotFailed();
            var cache = context.getBean(ManagedCache.class);
            assertThat(cache.get("config.parameters", "key", String.class, () -> "a")).isEqualTo("a");
            assertThat(cache.get("config.parameters", "key", String.class, () -> "b")).isEqualTo("b");
            assertThat(cache.snapshot("config.parameters").size()).isZero();
        });
    }

    @Test void globalDefaultsAreInheritedAndRegionOverridesRemainWithinDeclaredCaps() {
        runner.withPropertyValues("sma.cache.defaults.ttl=30s", "sma.cache.defaults.negative-ttl=5s",
                "sma.cache.defaults.max-entries=50", "sma.cache.regions[config.parameters].ttl=60s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var policy = context.getBean(CacheProperties.class).policy("config.parameters", CachePolicy.defaults());
                    assertThat(policy.ttl()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(policy.negativeTtl()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(policy.maxEntries()).isEqualTo(50);
                    assertThat(context.getBean(CacheRegions.class).regions()).extracting(CacheDescriptor::id)
                            .containsExactlyInAnyOrder("config.parameters","config.dictionary-options","config.preferences");
                });
        runner.withPropertyValues("sma.cache.defaults.ttl=600s").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("sma.cache.defaults.ttl=30s", "sma.cache.regions[config.parameters].ttl=600s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidOverridesAndUnknownProvidersFailStartupInsteadOfSilentlyFallingBack() {
        runner.withPropertyValues("sma.cache.regions[config.parameters].ttl=600s")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("sma.cache.provider=typo")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("sma.cache.regions[unknown.region].ttl=30s")
                .run(context -> assertThat(context).hasFailed());
    }
}
