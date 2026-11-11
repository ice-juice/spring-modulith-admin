package com.sma.admin.iam.internal.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.AccountCredentialGuard;
import com.sma.admin.iam.internal.persistence.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
public class AccountCredentialGuardService implements AccountCredentialGuard {
    private final StructureGuard guard;private final AccountMapper accounts;private final PasswordEncoder passwords;
    public AccountCredentialGuardService(StructureGuard guard,AccountMapper accounts,PasswordEncoder passwords){this.guard=guard;this.accounts=accounts;this.passwords=passwords;}
    public void lockOwnAccount(){CurrentUser.require();guard.lock();current();}
    public boolean matchesOwnCurrentPassword(String candidate){guard.lock();var row=current();return candidate!=null&&passwords.matches(candidate,row.getPasswordHash());}
    private UserEntity current(){
        var principal=CurrentUser.require();var row=accounts.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getId,principal.userId()).eq(UserEntity::getPortal,principal.portal()).last("FOR UPDATE"));
        if(row==null||!row.getEnabled())throw new BizException(CommonErrorCode.UNAUTHORIZED);return row;
    }
}
