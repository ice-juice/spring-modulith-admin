package com.sma.admin.identity.internal.service;
@org.springframework.boot.context.properties.ConfigurationProperties("sma.identity.http")
public record IdentityHttpProperties(Integer maximumConcurrent,Integer maximumJsonBytes){
    public IdentityHttpProperties{maximumConcurrent=maximumConcurrent==null?64:maximumConcurrent;maximumJsonBytes=maximumJsonBytes==null?65536:maximumJsonBytes;
        if(maximumConcurrent<1 || maximumConcurrent>256 || maximumJsonBytes<4096 || maximumJsonBytes>1048576)throw new IllegalArgumentException("Invalid application HTTP budget");}
}
