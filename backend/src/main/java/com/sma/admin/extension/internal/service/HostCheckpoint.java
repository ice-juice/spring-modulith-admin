package com.sma.admin.extension.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sma.extension.runtime.RuntimeCheckpoint;
import com.sma.extension.protocol.Checks;
import com.sma.admin.extension.internal.persistence.*;

@Service
public class HostCheckpoint implements RuntimeCheckpoint {
    private final RuntimeStateMapper mapper;
    private final ExtensionProperties properties;
    public HostCheckpoint(RuntimeStateMapper mapper,ExtensionProperties properties) { this.mapper=mapper;this.properties=properties; }
    private String id(String deployment) { return properties.nodeId()+":"+properties.environment()+":"+deployment; }
    @Override public long revision(String deployment) { var row=mapper.selectById(id(deployment));return row==null?0:row.getRevision(); }
    @Override @Transactional public void advance(String deployment,long expected,long next) {
        Checks.require(next>expected,"release-revision-conflict");
        if(expected==0 && mapper.selectById(id(deployment))==null) {
            var row=new RuntimeStateEntity();row.setId(id(deployment));row.setRevision(next);mapper.insert(row);
        } else Checks.require(mapper.update(new LambdaUpdateWrapper<RuntimeStateEntity>().eq(RuntimeStateEntity::getId,id(deployment))
                .eq(RuntimeStateEntity::getRevision,expected).set(RuntimeStateEntity::getRevision,next))==1,"release-revision-conflict");
    }
}
