package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class StorageProvisionServiceTests {
    @Test void migrationFailureMarksTheDurableOperationForReviewWithoutActivation() {
        var state = mock(StorageProvisionState.class);
        var migration = mock(StorageMigrationService.class);
        var work = new StorageProvisionState.Work(20, 30, 1, "shop-a", 1, true,"shop");
        when(state.begin(30, 1, "provision-operation-0001")).thenReturn(work);
        doThrow(new IllegalStateException("target unavailable")).when(migration).migrate("shop-a", 1,"shop");
        var service = new StorageProvisionService(state, migration);
        assertThatThrownBy(() -> service.provision(30, 1, "provision-operation-0001"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("target unavailable");
        var ordered = inOrder(state, migration);
        ordered.verify(state).begin(30, 1, "provision-operation-0001");
        ordered.verify(state).requireCurrent(work);
        ordered.verify(migration).migrate("shop-a", 1,"shop");
        ordered.verify(state).failed(work);
        verify(state, org.mockito.Mockito.never()).finish(work);
    }

    @Test void repeatedCompletedOperationDoesNotMigrateAgain() {
        var state = mock(StorageProvisionState.class);
        var migration = mock(StorageMigrationService.class);
        when(state.begin(30, 1, "provision-operation-0002"))
                .thenReturn(new StorageProvisionState.Work(20, 30, 2, "shop-a", 1, false,"shop"));
        var service = new StorageProvisionService(state, migration);
        assertThat(service.provision(30, 1, "provision-operation-0002")).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verifyNoInteractions(migration);
    }
}
