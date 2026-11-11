package com.sma.admin.auth.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.security.CurrentUser;

/** All writes to an account's security state share this lock, including password-only issuance. */
@Service
public class AccountSecurityStore {
    private final SessionGuardMapper guard;
    private final UserSecurityMapper states;
    private final TotpCredentialMapper totps;
    public AccountSecurityStore(SessionGuardMapper guard, UserSecurityMapper states, TotpCredentialMapper totps) {
        this.guard = guard; this.states = states; this.totps = totps;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public UserSecurityEntity lock(long userId, String portal) {
        guard.lock(userId, CurrentUser.tenantId());
        var state = states.selectOne(new LambdaQueryWrapper<UserSecurityEntity>().eq(UserSecurityEntity::getUserId, userId)
                .eq(UserSecurityEntity::getPortal, portal).last("FOR UPDATE"));
        if (state == null) {
            state = new UserSecurityEntity(); state.setUserId(userId); state.setPortal(portal); state.setSecurityVersion(0L); states.insert(state);
        }
        return state;
    }
    public long version(long userId, String portal) {
        var state = states.selectOne(new LambdaQueryWrapper<UserSecurityEntity>().eq(UserSecurityEntity::getUserId,userId).eq(UserSecurityEntity::getPortal,portal));
        return state == null ? 0 : state.getSecurityVersion();
    }
    public TotpCredentialEntity totp(long userId, String portal) {
        return totps.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,userId).eq(TotpCredentialEntity::getPortal,portal));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public TotpCredentialEntity lockedTotp(long userId,String portal) {
        return totps.selectOne(new LambdaQueryWrapper<TotpCredentialEntity>().eq(TotpCredentialEntity::getUserId,userId).eq(TotpCredentialEntity::getPortal,portal).last("FOR UPDATE"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void advance(UserSecurityEntity state) {
        if (state.getTenantId() != CurrentUser.tenantId()) throw new BizException(CommonErrorCode.FORBIDDEN);
        state.setSecurityVersion(Math.addExact(state.getSecurityVersion(), 1)); states.updateById(state);
    }
}
