package com.sma.admin.config;
import java.util.UUID;
import com.sma.admin.support.IntegrationTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
@IntegrationTest @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
class RedisBasicDataCacheTests extends BasicDataCacheContract {
    private static final String NAMESPACE = "p08basic-"+UUID.randomUUID();
    @Override long tenantBase() { return 8300; }
    @DynamicPropertySource static void redis(DynamicPropertyRegistry properties) {
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if(port<10000 || port>65535) throw new IllegalArgumentException("Require dedicated high localhost port");
        properties.add("sma.cache.provider", () -> "redis");
        properties.add("sma.cache.namespace", () -> NAMESPACE);
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> port);
        properties.add("spring.data.redis.timeout", () -> "500ms");
    }
}
