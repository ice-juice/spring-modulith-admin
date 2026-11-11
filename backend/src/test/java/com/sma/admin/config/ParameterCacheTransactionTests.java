package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class ParameterCacheTransactionTests {
    @Autowired ParameterService parameters;
    @Autowired ManagedCache cache;
    @Autowired CacheRevisionStore revisions;
    @Autowired PlatformTransactionManager transactions;

    @Test void rollbackCannotPoisonCommittedCacheAndCommitInvalidatesIt() {
        TenantScope.within(5801, () -> {
            parameters.saveGroup(null, new ParameterService.GroupInput("cachetest", "Cache test", null, 0, Map.of()));
            var row = parameters.save(null, input("cachetest.value", "original"));
            assertThat(parameters.stringValue("cachetest.value", "missing")).isEqualTo("original");
            long committedRevision = revisions.current("config.parameters");
            assertThat(committedRevision).isPositive();
            var tx = new TransactionTemplate(transactions);
            tx.execute(status -> {
                parameters.save(row.id(), input("cachetest.value", "rolled-back"));
                assertThat(parameters.stringValue("cachetest.value", "missing")).isEqualTo("rolled-back");
                status.setRollbackOnly(); return null;
            });
            assertThat(parameters.stringValue("cachetest.value", "missing")).isEqualTo("original");
            assertThat(revisions.current("config.parameters")).isEqualTo(committedRevision);
            tx.execute(status -> { parameters.save(row.id(), input("cachetest.value", "committed")); return null; });
            assertThat(parameters.stringValue("cachetest.value", "missing")).isEqualTo("committed");
            assertThat(revisions.current("config.parameters")).isEqualTo(committedRevision + 1);
            return null;
        });
    }

    @Test void securityParametersBypassOrdinaryCacheAndMissingValuesUseEachCallersFallback() {
        TenantScope.within(5802, () -> {
            parameters.saveGroup(null, new ParameterService.GroupInput("cachetest", "Cache test", null, 0, Map.of()));
            parameters.save(null, input("auth.cacheTest", "security"));
            assertThat(parameters.stringValue("auth.cacheTest", "missing")).isEqualTo("security");
            assertThat(cache.snapshot("config.parameters").size()).isZero();
            parameters.save(null,new ParameterService.Input("cachetest.secret","cachetest","Secret",null,"STRING","private",false,true,0,Map.of()));
            assertThat(parameters.stringValue("cachetest.secret", "missing")).isEqualTo("private");
            assertThat(cache.snapshot("config.parameters").size()).isZero();
            assertThat(parameters.stringValue("cachetest.absent", "first")).isEqualTo("first");
            assertThat(parameters.stringValue("cachetest.absent", "second")).isEqualTo("second");
            assertThat(cache.snapshot("config.parameters").hits()).isEqualTo(1);
            return null;
        });
    }

    private ParameterService.Input input(String key, String value) {
        return new ParameterService.Input(key, "cachetest", key, null, "STRING", value, false, false, 0, Map.of());
    }
}
