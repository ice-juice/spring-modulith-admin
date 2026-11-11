package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.PasswordStrengthPolicy;
import com.sma.admin.identity.IdentityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityPasswordWorkTests {
    @Test void sharedPolicyRejectsBeforeHashingAndUsesCanonicalLocalIdentifier(){
        var encoder=mock(PasswordEncoder.class);var policy=mock(PasswordStrengthPolicy.class);
        when(encoder.encode(anyString())).thenReturn("dummy");
        var work=new IdentityPasswordWork(encoder,policy,new IdentityPasswordWork.Settings(1,5));
        clearInvocations(encoder);
        when(policy.evaluate("Account","bad")).thenReturn(PasswordStrengthPolicy.Decision.INVALID_FORMAT);
        when(policy.evaluate("Account","weakpass1")).thenReturn(PasswordStrengthPolicy.Decision.WEAK);
        assertThatThrownBy(()->work.encode("Account@example.test","bad")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_FORMAT));
        assertThatThrownBy(()->work.encode("Account@example.test","weakpass1")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.WEAK_PASSWORD));
        verifyNoInteractions(encoder);
    }
    @Test void hashingIsBoundedAndFailedHashingReleasesCapacity()throws Exception{
        var encoder=mock(PasswordEncoder.class);var policy=mock(PasswordStrengthPolicy.class);when(policy.evaluate(anyString(),anyString())).thenReturn(PasswordStrengthPolicy.Decision.ACCEPTED);
        when(encoder.encode(anyString())).thenReturn("dummy");
        var work=new IdentityPasswordWork(encoder,policy,new IdentityPasswordWork.Settings(1,5));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(encoder.encode("Q7d4e10594")).thenAnswer(i->{entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("test deadline");throw new IllegalStateException("test encoder failure");});
        var executor=Executors.newSingleThreadExecutor();
        try{
            var first=executor.submit(()->work.encode("owner@example.test","Q7d4e10594"));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->work.encode("owner@example.test","Q7d4e10594")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
            release.countDown();assertThatThrownBy(()->first.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            when(encoder.encode("otherpass9")).thenReturn("encoded");assertThat(work.encode("owner@example.test","otherpass9")).isEqualTo("encoded");
        }finally{release.countDown();executor.shutdownNow();}
    }
    @Test void unknownAccountUsesWarmDummyHashAndLegacyVerificationDoesNotApplyWriteRules(){
        var encoder=mock(PasswordEncoder.class);var policy=mock(PasswordStrengthPolicy.class);when(encoder.encode(anyString())).thenReturn("warm-dummy");
        var work=new IdentityPasswordWork(encoder,policy,new IdentityPasswordWork.Settings(1,5));
        when(encoder.matches("Legacy!Password2025","existing-hash")).thenReturn(true);
        assertThat(work.matches("Legacy!Password2025","existing-hash")).isTrue();assertThat(work.matches("wrong-password",null)).isFalse();
        verify(encoder).matches("wrong-password","warm-dummy");verifyNoInteractions(policy);
        assertThat(work.matches("x".repeat(73),"existing-hash")).isFalse();assertThat(work.matches("汉".repeat(25),"existing-hash")).isFalse();
    }
}
