package com.sma.admin.iam.internal.service;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.iam.internal.persistence.*;

@Component
public class RolePermissionReference implements OrganizationReference {
    private final RolePermissionMapper mapper;
    public RolePermissionReference(RolePermissionMapper mapper) { this.mapper=mapper; }
    @Override public boolean permissionUsed(long id) {
        return mapper.selectCount(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getPermissionId,id))>0;
    }
}
