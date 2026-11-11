package com.sma.admin.auth.internal.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;

/** Pre-reserve budget in its own committed transaction; later failures cannot roll it back. */
@Service
public class SecurityRateLimit {
    private final SecurityThrottleMapper mapper;
    public SecurityRateLimit(SecurityThrottleMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void claim(String scope, String subject, int limit, Duration window) {
        if (!scope.matches("[A-Z_]{1,32}") || limit < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid security budget");
        String hash = Tokens.hash(subject);
        mapper.ensure(IdWorker.getId(), CurrentUser.tenantId(), scope, hash);
        var row = mapper.selectOne(new LambdaQueryWrapper<SecurityThrottleEntity>()
                .eq(SecurityThrottleEntity::getScope, scope).eq(SecurityThrottleEntity::getSubjectHash, hash).last("FOR UPDATE"));
        var now = mapper.now();
        if (!row.getExpiresAt().isAfter(now)) {
            row.setAttemptCount(0);
            row.setExpiresAt(now.plus(window));
        }
        if (row.getAttemptCount() >= limit) throw new BizException(AuthErrorCode.RATE_LIMITED);
        row.setAttemptCount(row.getAttemptCount() + 1);
        mapper.updateById(row);
    }
}
