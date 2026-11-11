package com.sma.admin.mail.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.mail.dispatch")
public record MailDispatchProperties(boolean enabled,int concurrency,int batchPerTenant,int tenantPageSize){
    public MailDispatchProperties{
        concurrency=concurrency==0?2:concurrency;batchPerTenant=batchPerTenant==0?10:batchPerTenant;tenantPageSize=tenantPageSize==0?100:tenantPageSize;
        if(concurrency<1||concurrency>16||batchPerTenant<1||batchPerTenant>100||tenantPageSize<1||tenantPageSize>100)
            throw new IllegalArgumentException("Invalid mail dispatch bounds");
    }
}
