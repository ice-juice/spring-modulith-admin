package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.support.*;

@IntegrationTest
class SessionConcurrencyTests {
    private static final String PASSWORD="Q7d4e10594";
    @Autowired SessionMapper rows;
    @Autowired UserService users;
    @Autowired SessionIssuer issuer;
    @Autowired SessionService sessions;
    @Autowired SessionManager manager;
    @Autowired ParameterService parameters;
    @Autowired com.sma.admin.config.internal.service.DefinedParameterService parameterLayers;
    @MockitoSpyBean SessionActivityService activity;
    private record Fixture(SmaPrincipal actor,String token,long session) {}

    @Test void anOldResolverCannotRelockOrOverwriteActivityAfterACompletedUnlock(){
        var f=fixture(98401,30);var before=Users.as(f.actor(),()->age(f));
        doAnswer(call->{manager.lock(f.session());manager.unlock(f.session(),PASSWORD,"192.0.2.101");return call.callRealMethod();})
                .when(activity).refresh(argThat(row->row!=null&&row.getId()==f.session()),eq(Duration.ofSeconds(30)));
        Users.as(f.actor(),()->{
            var resolved=sessions.resolve(f.token()).orElseThrow();assertThat(resolved.session().getLocked()).isFalse();
            assertThat(resolved.session().getLastSeenAt()).isAfter(before.getLastSeenAt());unchangedAuthority(before,rows.selectById(f.session()));return null;
        });
        verify(activity).refresh(argThat(row->row!=null&&row.getId()==f.session()),eq(Duration.ofSeconds(30)));
    }
    @Test void aStaleActivityTouchCannotUndoAManualLockAndReportsTheCurrentLockedState(){
        var f=fixture(98402,0);var before=Users.as(f.actor(),()->age(f));
        doAnswer(call->{manager.lock(f.session());return call.callRealMethod();})
                .when(activity).refresh(argThat(row->row!=null&&row.getId()==f.session()),eq(Duration.ZERO));
        Users.as(f.actor(),()->{
            var resolved=sessions.resolve(f.token()).orElseThrow();assertThat(resolved.session().getLocked()).isTrue();
            var after=rows.selectById(f.session());assertThat(after.getLastSeenAt()).isEqualTo(before.getLastSeenAt());unchangedAuthority(before,after);return null;
        });
    }
    @Test void logoutCommittedAfterTheInitialReadCannotBeOverwrittenOrReturnedAsAnActiveSession(){
        var f=fixture(98403,0);Users.as(f.actor(),()->age(f));
        doAnswer(call->{manager.logout(f.session());return call.callRealMethod();})
                .when(activity).refresh(argThat(row->row!=null&&row.getId()==f.session()),eq(Duration.ZERO));
        Users.as(f.actor(),()->{
            assertThat(sessions.resolve(f.token())).isEmpty();assertThat(rows.selectById(f.session())).isNull();assertThat(sessions.resolve(f.token())).isEmpty();return null;
        });
    }
    @Test void anActuallyIdleSessionLocksAndOnlyAValidPasswordUnlocksIt(){
        var f=fixture(98404,30);Users.as(f.actor(),()->{
            var before=age(f);assertThat(sessions.resolve(f.token()).orElseThrow().session().getLocked()).isTrue();
            assertThatThrownBy(()->manager.unlock(f.session(),"wrong","192.0.2.104")).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
            assertThat(rows.selectById(f.session()).getLocked()).isTrue();manager.unlock(f.session(),PASSWORD,"192.0.2.104");
            assertThat(sessions.resolve(f.token()).orElseThrow().session().getLocked()).isFalse();unchangedAuthority(before,rows.selectById(f.session()));return null;
        });
    }
    @Test void activityNeverMovesBackwardsAndTenantOrOwnerMismatchCannotUpdateTheSession(){
        var f=fixture(98405,0);var before=Users.as(f.actor(),()->{
            var row=rows.selectById(f.session());row.setLastSeenAt(rows.now().plusSeconds(120));rows.updateById(row);row=rows.selectById(f.session());
            manager.lock(f.session());manager.unlock(f.session(),PASSWORD,"192.0.2.105");
            assertThat(rows.touch(f.session(),f.actor().userId(),"admin")).isEqualTo(1);
            assertThat(rows.touch(f.session(),f.actor().userId()+1,"admin")).isZero();assertThat(rows.lockSession(f.session(),f.actor().userId(),"other")).isZero();
            var after=rows.selectById(f.session());assertThat(after.getLastSeenAt()).isEqualTo(row.getLastSeenAt());unchangedAuthority(row,after);return after;
        });
        Users.as(Users.principal(f.actor().userId(),98406,DataAccess.unrestricted()),()->{assertThat(rows.lockSession(f.session(),f.actor().userId(),"admin")).isZero();return null;});
        Users.as(f.actor(),()->{assertThat(rows.selectById(f.session()).getLocked()).isFalse();assertThat(rows.selectById(f.session()).getLastSeenAt()).isEqualTo(before.getLastSeenAt());return null;});
    }
    private Fixture fixture(long tenant,long idle){
        var admin=new SmaPrincipal(101,"admin",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of("config:parameter:update"),"UTC");
        return Users.as(admin,()->{
            parameterLayers.override("auth.autoLockSeconds",parameterLayers.get("auth.autoLockSeconds").snapshot().revision(),Long.toString(idle));
            var input=new UserService.Input("activity-user","Activity User",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),"zh-CN","UTC");
            users.create(new UserService.Create(input,PASSWORD));var account=users.authenticate("activity-user",PASSWORD,"admin").orElseThrow();
            var login=issuer.issue(account,false,Duration.ofHours(1),"192.0.2.100","session-concurrency");
            var session=rows.selectOne(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getTokenHash,Tokens.hash(login.token())));
            return new Fixture(new SmaPrincipal(account.id(),account.username(),tenant,"admin",null,false,DataAccess.own(),Set.of(),"UTC"),login.token(),session.getId());
        });
    }
    private SessionEntity age(Fixture f){var row=rows.selectById(f.session());row.setLastSeenAt(rows.now().minusSeconds(300));rows.updateById(row);return rows.selectById(f.session());}
    private void unchangedAuthority(SessionEntity before,SessionEntity after){
        assertThat(after.getTokenHash()).isEqualTo(before.getTokenHash());assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());
        assertThat(after.getCredentialVersion()).isEqualTo(before.getCredentialVersion());assertThat(after.getSecurityVersion()).isEqualTo(before.getSecurityVersion());
        assertThat(after.getTenantSecurityGeneration()).isEqualTo(before.getTenantSecurityGeneration());assertThat(after.getAuthenticationMethod()).isEqualTo(before.getAuthenticationMethod());
        assertThat(after.getPasswordVerifiedAt()).isEqualTo(before.getPasswordVerifiedAt());assertThat(after.getMfaVerifiedAt()).isEqualTo(before.getMfaVerifiedAt());
    }
}
