package com.sma.admin.iam;
import com.sma.admin.support.IntegrationTest;
@IntegrationTest
class LocalIamDisplayCacheTests extends IamDisplayCacheContract {
    @Override long tenantBase() { return 8500; }
}
