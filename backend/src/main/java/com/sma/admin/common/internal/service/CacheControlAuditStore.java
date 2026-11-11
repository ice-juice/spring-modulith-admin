package com.sma.admin.common.internal.service;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.cache.CacheGovernance;
import com.sma.admin.common.internal.persistence.*;

/** Successful control audit is immutable and commits with the state; request denials use the existing HTTP audit. */
@Service
public class CacheControlAuditStore {
    private final CacheControlAuditMapper mapper;
    private final JsonMapper json=JsonMapper.builder().build();
    public CacheControlAuditStore(CacheControlAuditMapper mapper){this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(String region,CacheGovernance.Action action,String reason,String portal,CacheGovernance.State before,CacheGovernance.State after){
        var row=new CacheControlAuditEntity();row.setRegion(region);row.setControlVersion(after.version());
        row.setAction(action.name());row.setReason(reason);row.setPortal(portal);
        row.setBeforeState(json.writeValueAsString(before));row.setAfterState(json.writeValueAsString(after));mapper.insert(row);
    }
    @Transactional(readOnly=true)
    public List<CacheGovernance.History> history(String region,int limit){
        if(limit<1||limit>100)throw new IllegalArgumentException("Invalid audit limit");
        return mapper.selectList(new LambdaQueryWrapper<CacheControlAuditEntity>().eq(CacheControlAuditEntity::getRegion,region)
                .orderByDesc(CacheControlAuditEntity::getControlVersion).last("LIMIT "+limit)).stream()
                .map(row->new CacheGovernance.History(row.getId(),row.getCreatedAt(),row.getCreatedBy(),row.getPortal(),CacheGovernance.Action.valueOf(row.getAction()),row.getReason(),
                        json.readValue(row.getBeforeState(),CacheGovernance.State.class),json.readValue(row.getAfterState(),CacheGovernance.State.class))).toList();
    }
}
