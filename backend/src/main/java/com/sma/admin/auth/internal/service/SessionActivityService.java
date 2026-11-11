package com.sma.admin.auth.internal.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.CurrentUser;

/** Narrow conditional writes; an older request cannot overwrite newly committed session state. */
@Service
public class SessionActivityService {
    private final SessionMapper sessions;
    public SessionActivityService(SessionMapper sessions){this.sessions=sessions;}
    public SessionEntity refresh(SessionEntity observed,Duration idle){
        if(idle.isNegative())throw new IllegalArgumentException("Negative session idle limit");
        if(observed==null||Boolean.TRUE.equals(observed.getLocked()))return observed;
        var now=sessions.now();var current=observed;var user=CurrentUser.require();
        if(!idle.isZero()&&observed.getLastSeenAt().plus(idle).isBefore(now)){
            sessions.autoLock(observed.getId(),user.userId(),user.portal(),observed.getLastSeenAt(),now.minus(idle));
            current=sessions.selectById(observed.getId());
        }
        if(current!=null&&!current.getLocked()&&current.getLastSeenAt().isBefore(now.minusSeconds(60))){
            sessions.touch(current.getId(),user.userId(),user.portal());
            current=sessions.selectById(current.getId());
        }
        return current;
    }
}
