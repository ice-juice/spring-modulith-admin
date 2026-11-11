package com.sma.admin.config;
import com.sma.admin.support.IntegrationTest;
@IntegrationTest
class LocalBasicDataCacheTests extends BasicDataCacheContract {
    @Override long tenantBase() { return 8100; }
}
