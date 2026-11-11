package com.sma.admin.mail.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryEntity;
import org.junit.jupiter.api.Test;

class MailDeliveryGuardsTests {
    @Test void legacyTasksRemainValidButDeclaredGuardNeverFallsBackWhenMissing(){
        var registry=new MailDeliveryGuards(List.of());var row=new MailDeliveryEntity();assertThat(registry.mayDeliver(row)).isTrue();
        row.setGuardOwner("identity.email");row.setGuardReference("123");assertThat(registry.mayDeliver(row)).isFalse();
        assertThat(registry.valid(new MailSendApi.Guard("identity.email","123"))).isFalse();
        row.setGuardOwner(null);assertThat(registry.mayDeliver(row)).isFalse();
    }
    @Test void gatesReceiveOwnedMetadataOnlyAndDuplicateRegistrationsFail(){
        var gate=mock(MailDeliveryGate.class);when(gate.owner()).thenReturn("test.owner");var registry=new MailDeliveryGuards(List.of(gate));
        var row=new MailDeliveryEntity();row.setId(12L);row.setTenantId(13L);row.setApplication("shop");row.setScene("notice");row.setExpiresAt(Instant.EPOCH);
        row.setGuardOwner("test.owner");row.setGuardReference("object:1");
        var view=new MailDeliveryGate.Delivery(13,12,"shop","notice","object:1",Instant.EPOCH);when(gate.mayDeliver(view)).thenReturn(true);
        assertThat(registry.mayDeliver(row)).isTrue();verify(gate).mayDeliver(view);
        assertThat(registry.valid(new MailSendApi.Guard("test.owner","secret@example.test"))).isFalse();
        assertThatThrownBy(()->new MailDeliveryGuards(List.of(gate,gate))).isInstanceOf(IllegalStateException.class);
    }
}
