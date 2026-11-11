package com.sma.admin.auth.internal.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;

@Service
public class SecurityRevocation {
    private final SessionMapper sessions;private final SecurityChallengeMapper challenges;private final AccountSecurityStore security;private final ApplicationEventPublisher events;
    public SecurityRevocation(SessionMapper sessions,SecurityChallengeMapper challenges,AccountSecurityStore security,ApplicationEventPublisher events){this.sessions=sessions;this.challenges=challenges;this.security=security;this.events=events;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void changed(UserSecurityEntity state){
        security.advance(state);
        var query=new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId,state.getUserId()).eq(SessionEntity::getPortal,state.getPortal());
        var rows=sessions.selectList(query);sessions.delete(query);
        challenges.delete(new LambdaQueryWrapper<SecurityChallengeEntity>().eq(SecurityChallengeEntity::getUserId,state.getUserId()).eq(SecurityChallengeEntity::getPortal,state.getPortal()));
        for(var row:rows)events.publishEvent(new SessionEndedEvent(state.getTenantId(),state.getUserId(),row.getId(),"SECURITY_CHANGED"));
    }
}
