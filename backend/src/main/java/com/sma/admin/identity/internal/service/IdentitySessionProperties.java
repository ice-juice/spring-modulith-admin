package com.sma.admin.identity.internal.service;
@org.springframework.boot.context.properties.ConfigurationProperties("sma.identity.sessions")
public record IdentitySessionProperties(int maximumPerSubject){
    public IdentitySessionProperties{maximumPerSubject=maximumPerSubject==0?10:maximumPerSubject;if(maximumPerSubject<1 || maximumPerSubject>100)throw new IllegalArgumentException("Invalid identity session budget");}
}
