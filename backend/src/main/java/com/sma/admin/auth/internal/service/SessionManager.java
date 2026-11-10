package com.sma.admin.auth.internal.service;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.auth.*;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.iam.AccountQuery;

@Service @Transactional(readOnly=true)
public class SessionManager {
    private final SessionMapper mapper;
    private final AccountQuery accounts;
    private final ApplicationEventPublisher events;
    private final LoginThrottle throttle;
    public SessionManager(SessionMapper mapper,AccountQuery accounts,ApplicationEventPublisher events,LoginThrottle throttle) {
        this.mapper=mapper; this.accounts=accounts; this.events=events; this.throttle=throttle;
    }
    public record View(Long id,Long userId,String username,String name,String portal,String ip,String userAgent,
            Instant createdAt,Instant lastSeenAt,Instant expiresAt,boolean locked,boolean current) {}
    public PageResult<View> page(PageQuery page,String keyword,long currentSessionId) {
        var paging=page.<SessionEntity>toPage(Map.of("createdAt","created_at","lastSeenAt","last_seen_at"));
        var query=new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getPortal,CurrentUser.portal()).gt(SessionEntity::getExpiresAt,Instant.now()).orderByDesc(SessionEntity::getLastSeenAt);
        if(keyword!=null&&!keyword.isBlank()) {
            Set<Long> ids=accounts.searchIds(keyword);
            if(ids.isEmpty()) return new PageResult<>(List.of(),0,paging.getCurrent(),paging.getSize());
            query.in(SessionEntity::getUserId,ids);
        }
        return PageResult.of(mapper.selectPage(paging,query),row -> {
            var account=accounts.findById(row.getUserId()).orElse(null);
            return new View(row.getId(),row.getUserId(),account==null?"":account.username(),account==null?"":account.name(),row.getPortal(),row.getIp(),
                    row.getUserAgent(),row.getCreatedAt(),row.getLastSeenAt(),row.getExpiresAt(),row.getLocked(),row.getId()==currentSessionId);
        });
    }
    @Transactional public void forceLogout(long sessionId) {
        var row=require(sessionId);
        var target=accounts.findById(row.getUserId());
        if(target.isPresent()&&target.get().superAdmin()&&!CurrentUser.require().superAdmin()) throw new AccessDeniedException("Protected administrator session");
        end(row,"FORCED");
    }
    @Transactional public void logout(long sessionId) { end(owned(sessionId),"LOGOUT"); }
    @Transactional public void lock(long sessionId) {
        var row=owned(sessionId); row.setLocked(true); mapper.updateById(row);
    }
    @Transactional public void unlock(long sessionId,String password,String ip) {
        var row=owned(sessionId);
        String key=throttle.key(ip,"unlock:"+row.getUserId());
        throttle.check(key);
        if(!accounts.matchesPassword(row.getUserId(),password)) { throttle.fail(key); throw new BizException(AuthErrorCode.INVALID_CREDENTIALS); }
        throttle.success(key); row.setLocked(false); row.setLastSeenAt(Instant.now()); mapper.updateById(row);
    }
    private SessionEntity owned(long id) {
        var row=require(id);
        if(row.getUserId()!=CurrentUser.require().userId()) throw new AccessDeniedException("Not your session");
        return row;
    }
    private SessionEntity require(long id) {
        var row=mapper.selectOne(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getId,id).eq(SessionEntity::getPortal,CurrentUser.portal()));
        if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row;
    }
    private void end(SessionEntity row,String reason) {
        mapper.deleteById(row.getId()); events.publishEvent(new SessionEndedEvent(CurrentUser.tenantId(),row.getUserId(),row.getId(),reason));
    }
}
