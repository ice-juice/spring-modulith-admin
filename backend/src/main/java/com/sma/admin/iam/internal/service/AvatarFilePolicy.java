package com.sma.admin.iam.internal.service;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.persistence.*;

@Component
public class AvatarFilePolicy implements FileAccessPolicy {
 private final AccountMapper accounts;
 public AvatarFilePolicy(AccountMapper accounts){this.accounts=accounts;}
 @Override public boolean canRead(long fileId,SmaPrincipal user){return accounts.selectCount(new LambdaQueryWrapper<UserEntity>()
  .eq(UserEntity::getAvatarFileId,fileId).eq(UserEntity::getPortal,user.portal()).eq(UserEntity::getEnabled,true))>0;}
 @Override public boolean inUse(long fileId){return accounts.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getAvatarFileId,fileId))>0;}
}
