package com.sma.admin.auth.internal.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;

@Service
public class SecurityChallenges {
    private final SecurityChallengeMapper mapper;
    private final SecurityThrottleMapper clock;
    private final TenantAuthenticationGuard tenants;
    public SecurityChallenges(SecurityChallengeMapper mapper, SecurityThrottleMapper clock,TenantAuthenticationGuard tenants) { this.mapper=mapper;this.clock=clock;this.tenants=tenants; }
    public Instant now() { return clock.now(); }
    @Transactional(propagation=Propagation.MANDATORY)
    public String insert(SecurityChallengeEntity challenge) {
        long tenant=com.sma.admin.common.security.CurrentUser.tenantId();
        if(challenge.getTenantSecurityGeneration()==null) challenge.setTenantSecurityGeneration(tenants.capture(tenant,challenge.getPortal()));
        else tenants.require(tenant,challenge.getPortal(),challenge.getTenantSecurityGeneration());
        String token=Tokens.create();challenge.setTokenHash(Tokens.hash(token));challenge.setAttemptCount(0);
        mapper.insert(challenge);return token;
    }
    /** Reserve an attempt before acquiring account locks. Errors in the caller cannot refund it. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public SecurityChallengeEntity reserve(String token, String purpose) {
        var row=find(token,true);var now=now();
        requireUsable(row,purpose,now);
        if(row.getAttemptCount()>=5)throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        row.setAttemptCount(row.getAttemptCount()+1);mapper.updateById(row);return row;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public SecurityChallengeEntity lock(String token,String purpose) {
        var row=find(token,true);requireUsable(row,purpose,now());return row;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void consume(SecurityChallengeEntity challenge) {
        challenge.setConsumedAt(now());mapper.updateById(challenge);
    }
    private SecurityChallengeEntity find(String token,boolean locked) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        var query=new LambdaQueryWrapper<SecurityChallengeEntity>().eq(SecurityChallengeEntity::getTokenHash,Tokens.hash(token));
        if(locked)query.last("FOR UPDATE");return mapper.selectOne(query);
    }
    private void requireUsable(SecurityChallengeEntity row,String purpose,Instant now) {
        if(row==null||!row.getPurpose().equals(purpose)||row.getConsumedAt()!=null||!row.getExpiresAt().isAfter(now))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
        if(!tenants.active(row.getTenantId(),row.getPortal(),row.getTenantSecurityGeneration()))throw new BizException(AuthErrorCode.INVALID_CHALLENGE);
    }
}
