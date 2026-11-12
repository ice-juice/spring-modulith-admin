package com.sma.admin.common.request;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sma.admin.common.internal.service.BusinessLedgerService;
import com.sma.admin.common.internal.service.BusinessLedgerResourceFactoryService;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;

class BusinessLedgerBoundaryTests {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<BusinessLedgerProvider> providers = mock(ObjectProvider.class);
    private final BusinessLedgerProvider provider = mock(BusinessLedgerProvider.class);
    private final BusinessLedger.Session delegate = mock(BusinessLedger.Session.class);
    private final BusinessLedger ledger = new BusinessLedgerService(providers);

    @Test void missingBindingFailsWithoutFallingBackToPlatformLedger() {
        var user = Users.principal(1, 17, DataAccess.none());
        assertThatThrownBy(() -> Users.as(user, () -> ledger.open("shop")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
        when(providers.getIfAvailable()).thenReturn(provider);
        assertThatThrownBy(() -> Users.as(user, () -> ledger.open("shop")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not bound");
        verify(provider).open(17, "shop");
    }

    @Test void resourceIsSelectedBeforeTransactionAndSessionStaysWithTrustedTenant() {
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.open(17, "shop")).thenReturn(delegate);
        var owner = Users.principal(1, 17, DataAccess.none());
        var other = Users.principal(2, 18, DataAccess.none());
        var session = Users.as(owner, () -> ledger.open("shop"));
        Users.as(owner, () -> { session.receiptByKey("original-key-12345"); return null; });
        verify(delegate).receiptByKey("original-key-12345");
        assertThatThrownBy(() -> Users.as(other, () -> session.receiptByKey("original-key-12345")))
                .isInstanceOf(AccessDeniedException.class);
        session.close();
        session.close();
        verify(delegate).close();
        assertThatThrownBy(() -> Users.as(owner, () -> session.receiptByKey("original-key-12345")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        verifyNoMoreInteractions(delegate);
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThatThrownBy(() -> Users.as(owner, () -> ledger.open("shop")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("before starting");
        } finally {
            TransactionSynchronizationManager.clear();
            session.close();
        }
    }

    @Test void invalidationRequiresThePinnedTenantsWritableTransaction() {
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.open(17, "shop")).thenReturn(delegate);
        var owner = Users.principal(1, 17, DataAccess.none());
        var session = Users.as(owner, () -> ledger.open("shop"));
        assertThatThrownBy(() -> Users.as(owner, () -> { session.invalidate("shop.items"); return null; }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("writable");
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            assertThatThrownBy(() -> Users.as(owner, () -> { session.invalidate("shop.items"); return null; }))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("writable");
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            Users.as(owner, () -> { session.invalidate("shop.items"); return null; });
            verify(delegate).invalidate("shop.items");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test void resourceFactoryRejectsMissingPersistenceGuardsAndMixedResources() {
        var source = mock(DataSource.class);
        var other = mock(DataSource.class);
        var factory = mock(SqlSessionFactory.class);
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setEnvironment(new Environment("business", new JdbcTransactionFactory(), source));
        when(factory.getConfiguration()).thenReturn(configuration);
        var resources = new BusinessLedgerResourceFactoryService(
                new RequestGovernanceProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "v1", "", 100L, 8));
        assertThatThrownBy(() -> resources.prepare("shop-mysql", 1, source, factory, new DataSourceTransactionManager(other)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("one data source");
        assertThatThrownBy(() -> resources.prepare("shop-mysql", 1, source, factory, new DataSourceTransactionManager(source)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("interceptors");
    }
}
