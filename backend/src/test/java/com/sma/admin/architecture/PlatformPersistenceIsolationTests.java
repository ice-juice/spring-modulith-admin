package com.sma.admin.architecture;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;

class PlatformPersistenceIsolationTests {
    @Test void onlyReviewedPlatformMetadataCanOmitTenantIsolation(){
        var global=ArchitectureRulesTests.CLASSES.stream().filter(type->type.isAnnotatedWith(TableName.class))
                .filter(type->!type.isAssignableTo(TenantEntity.class)).map(type->type.getName()).toList();
        assertThat(global).containsExactlyInAnyOrder(
                "com.sma.admin.tenant.internal.persistence.TenantRecordEntity",
                "com.sma.admin.tenant.internal.persistence.DomainEntity",
                "com.sma.admin.config.internal.persistence.SharedHeadEntity",
                "com.sma.admin.config.internal.persistence.SharedRevisionEntity",
                "com.sma.admin.config.internal.persistence.DictionaryCodeEntity",
                "com.sma.admin.common.internal.persistence.CacheNodeGroupEntity",
                "com.sma.admin.common.internal.persistence.CacheNodeLeaseEntity",
                "com.sma.admin.common.internal.persistence.IdBlockEntity",
                "com.sma.admin.extension.internal.persistence.RuntimeStateEntity",
                "com.sma.admin.extension.internal.persistence.RuntimeReferenceEntity",
                "com.sma.admin.openapi.internal.persistence.LoginBudgetEntity",
                "com.sma.admin.openapi.internal.persistence.LoginBudgetGuardEntity");
    }
}
