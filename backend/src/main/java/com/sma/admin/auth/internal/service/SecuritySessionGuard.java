package com.sma.admin.auth.internal.service;

import org.springframework.stereotype.Service;
import com.sma.admin.auth.internal.persistence.SessionMapper;
import com.sma.admin.auth.internal.persistence.SessionEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.*;

@Service
public class SecuritySessionGuard {
    private final SessionMapper sessions;private final SessionValidity validity;private final AccountQuery accounts;
    public SecuritySessionGuard(SessionMapper sessions,SessionValidity validity,AccountQuery accounts){this.sessions=sessions;this.validity=validity;this.accounts=accounts;}
    public AccountView require(long sessionId){
        var principal=CurrentUser.require();var query=new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getId,sessionId);
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())query.last("FOR UPDATE");
        var session=sessions.selectOne(query);
        if(session==null||session.getUserId()!=principal.userId()||!session.getPortal().equals(principal.portal())||!validity.valid(session,false))throw new BizException(CommonErrorCode.UNAUTHORIZED);
        return accounts.findById(principal.userId()).orElseThrow(()->new BizException(CommonErrorCode.UNAUTHORIZED));
    }
}
