package com.sma.admin.auth.internal.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;
import com.sma.admin.iam.*;

/** IAM-owned SPI implemented using auth-owned state; no dependency on IAM's UserService/AccountQuery. */
@Service
public class IndependentPasswordPolicy implements AccountPasswordPolicy {
    private final AccountSecurityStore security;private final SecurityPasswordMapper passwords;private final SecurityPasswordHash hash;
    public IndependentPasswordPolicy(AccountSecurityStore security,SecurityPasswordMapper passwords,SecurityPasswordHash hash){this.security=security;this.passwords=passwords;this.hash=hash;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void validate(long userId,String portal,String candidate){
        security.lock(userId,portal);
        var row=passwords.selectOne(new LambdaQueryWrapper<SecurityPasswordEntity>().eq(SecurityPasswordEntity::getUserId,userId).eq(SecurityPasswordEntity::getPortal,portal).last("FOR UPDATE"));
        if(row!=null&&hash.matches(candidate,row.getPasswordHash(),row.getPepperKeyId()))throw new BizException(IamErrorCode.PASSWORD_CONFLICT);
    }
}
