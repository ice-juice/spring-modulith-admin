package com.sma.admin.common.internal;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.internal.persistence.DatabaseIdentifierGenerator;

@Configuration(proxyBeanMethods=false)
class IdentifierConfiguration {
    @Bean IdentifierGenerator governedIdentifierGenerator(DataSource source){
        var generator=new DatabaseIdentifierGenerator(source);
        IdWorker.setIdentifierGenerator(generator);
        return generator;
    }
    @Bean com.sma.admin.common.persistence.IdentifierAllocation identifierAllocation(IdentifierGenerator generator){
        return ()->generator.nextId(null).longValue();
    }
}
