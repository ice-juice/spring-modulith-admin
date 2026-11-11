package com.sma.admin.common.internal.cache;

import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest @AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT",matches="[1-9][0-9]{3,4}")
class RedisCacheWarmupExecutionTests extends CacheWarmupExecutionTests {
    private static final String NAMESPACE="p08warm-"+UUID.randomUUID();
    @Override long tenantBase(){return 97900;}
    @DynamicPropertySource static void redis(DynamicPropertyRegistry properties){
        int port=Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if(port<10000||port>65535)throw new IllegalArgumentException("Require dedicated high localhost port");
        properties.add("sma.cache.provider",()->"redis");properties.add("sma.cache.namespace",()->NAMESPACE);
        properties.add("spring.data.redis.host",()->"127.0.0.1");properties.add("spring.data.redis.port",()->port);properties.add("spring.data.redis.timeout",()->"500ms");
    }
    @AfterAll static void removeOnlyThisSuiteKeys(@Autowired StringRedisTemplate redis,@Autowired com.sma.admin.common.cache.ManagedCache cache){
        ((DurableManagedCache)cache).close();var store=new RedisCacheStore(redis,NAMESPACE);var keys=new ArrayList<String>();
        for(long tenant=97901;tenant<=97910;tenant++)keys.add(store.epochKey(REGION,tenant));
        redis.delete(keys);
    }
}
