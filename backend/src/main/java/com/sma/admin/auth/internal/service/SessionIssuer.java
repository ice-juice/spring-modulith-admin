package com.sma.admin.auth.internal.service;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.iam.AccountView;

@Service
public class SessionIssuer {
    private final SessionMapper mapper;
    private final SessionGuardMapper guard;
    private final LoginEvents events;
    public SessionIssuer(SessionMapper mapper,SessionGuardMapper guard,LoginEvents events) { this.mapper=mapper; this.guard=guard; this.events=events; }
    @Transactional
    public SessionService.LoginResult issue(AccountView user,boolean singleTerminal,Duration ttl,String ip,String userAgent) {
        return issue(user,singleTerminal,ttl,ip,userAgent,false);
    }
    @Transactional
    public SessionService.LoginResult issue(AccountView user,boolean singleTerminal,Duration ttl,String ip,String userAgent,boolean locked) {
        guard.lock(user.id(),user.tenantId());
        if(singleTerminal) mapper.delete(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId,user.id()).eq(SessionEntity::getPortal,user.portal()));
        String token=Tokens.create();
        var row=new SessionEntity(); row.setUserId(user.id()); row.setPortal(user.portal()); row.setTokenHash(Tokens.hash(token));
        row.setExpiresAt(Instant.now().plus(ttl)); row.setLastSeenAt(Instant.now()); row.setIp(ip);
        row.setUserAgent(userAgent==null?null:userAgent.substring(0,Math.min(userAgent.length(),512))); row.setLocked(locked);
        row.setCredentialVersion(user.credentialVersion()); mapper.insert(row);
        events.record(user.id(),user.username(),user.portal(),"SUCCESS",ip,userAgent);
        return new SessionService.LoginResult(token,row.getExpiresAt());
    }
}
