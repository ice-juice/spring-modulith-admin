package com.sma.admin.auth.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;

class CaptchaTests {
    @Test
    void caseInsensitiveChallengesExpireAndAreConsumedOnce() {
        var mapper=mock(CaptchaMapper.class);
        var random=mock(SecureRandom.class); when(random.nextInt(anyInt())).thenReturn(0);
        var now=Instant.parse("2026-11-10T00:00:00Z");
        var service=new CaptchaService(mapper,random,Clock.fixed(now,ZoneOffset.UTC));
        AtomicReference<CaptchaEntity> inserted=new AtomicReference<>();
        doAnswer(call -> { var row=call.getArgument(0,CaptchaEntity.class); row.setId(1L); inserted.set(row); return 1; }).when(mapper).insert(any(CaptchaEntity.class));
        var challenge=service.issue();
        assertThat(challenge.image()).startsWith("data:image/png;base64,");
        var row=inserted.get();
        assertThat(row.getExpiresAt()).isEqualTo(now.plusSeconds(120));
        when(mapper.selectOne(any())).thenReturn(row);
        when(mapper.deleteById(1L)).thenReturn(1,0);
        service.verify(challenge.id(),"aaaaa");
        assertThatThrownBy(() -> service.verify(challenge.id(),"AAAAA")).isInstanceOf(BizException.class);
        row.setExpiresAt(now.minusSeconds(1));
        when(mapper.deleteById(1L)).thenReturn(1);
        assertThatThrownBy(() -> service.verify(challenge.id(),"AAAAA")).isInstanceOf(BizException.class);
    }
}
