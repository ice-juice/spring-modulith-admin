package com.sma.admin.iam.internal.service;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.iam.internal.persistence.*;
@Component
public class NavigationReference implements OrganizationReference {
    private final NavigationMenuMapper mapper;
    public NavigationReference(NavigationMenuMapper mapper) { this.mapper=mapper; }
    @Override public boolean permissionUsed(long id) {
        return mapper.selectCount(new LambdaQueryWrapper<NavigationMenuEntity>().eq(NavigationMenuEntity::getPermissionId,id))>0;
    }
}
