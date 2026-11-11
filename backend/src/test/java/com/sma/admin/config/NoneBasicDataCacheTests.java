package com.sma.admin.config;
import com.sma.admin.support.IntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
@IntegrationTest @TestPropertySource(properties="sma.cache.provider=none")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class NoneBasicDataCacheTests extends BasicDataCacheContract {
    @Override long tenantBase() { return 8200; }
}
