package com.sma.admin.tenant.internal.service;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.TenantWorkQuery;
import com.sma.admin.tenant.internal.persistence.TenantRecordEntity;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Directory remains owned by tenant. Mail never enumerates another module's table. */
@Service
@Transactional(readOnly=true)
public class TenantWorkDirectory implements TenantWorkQuery {
    private final TenantRecordMapper tenants;
    public TenantWorkDirectory(TenantRecordMapper tenants){this.tenants=tenants;}
    @Override public List<Entry> page(long afterId,int limit){
        // Host scheduling has no request authentication. A human/machine/app request may not enumerate tenants.
        if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Background directory only");
        if(afterId<0 || limit<1 || limit>100)throw new IllegalArgumentException("Invalid tenant work page");
        return tenants.selectList(new LambdaQueryWrapper<TenantRecordEntity>().gt(TenantRecordEntity::getId,afterId)
                .orderByAsc(TenantRecordEntity::getId).last("LIMIT "+limit)).stream()
                .map(row->new Entry(row.getId(),"ACTIVE".equals(row.getStatus()))).toList();
    }
}
