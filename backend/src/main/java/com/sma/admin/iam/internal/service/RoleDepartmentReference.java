package com.sma.admin.iam.internal.service;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.iam.internal.persistence.RoleDepartmentEntity;
import com.sma.admin.iam.internal.persistence.RoleDepartmentMapper;

@Component
public class RoleDepartmentReference implements OrganizationReference {
    private final RoleDepartmentMapper mapper;
    public RoleDepartmentReference(RoleDepartmentMapper mapper) { this.mapper = mapper; }
    @Override
    public boolean departmentUsed(long id) {
        return mapper.selectCount(new LambdaQueryWrapper<RoleDepartmentEntity>().eq(RoleDepartmentEntity::getDepartmentId,id)) > 0;
    }
}
