package com.sma.admin.iam.internal.service;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.iam.internal.persistence.*;

@Component
public class UserOrganizationReference implements OrganizationReference {
    private final AccountMapper accounts;
    private final UserPostMapper posts;
    private final UserRoleMapper roles;
    public UserOrganizationReference(AccountMapper accounts,UserPostMapper posts,UserRoleMapper roles) {
        this.accounts=accounts; this.posts=posts; this.roles=roles;
    }
    @Override public boolean departmentUsed(long id) {
        return accounts.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDeptId,id))>0;
    }
    @Override public boolean postUsed(long id) { return posts.selectCount(new LambdaQueryWrapper<UserPostEntity>().eq(UserPostEntity::getPostId,id))>0; }
    @Override public boolean roleUsed(long id) { return roles.selectCount(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId,id))>0; }
}
