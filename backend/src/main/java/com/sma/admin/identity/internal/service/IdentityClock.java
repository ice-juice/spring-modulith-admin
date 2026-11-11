package com.sma.admin.identity.internal.service;
@org.springframework.stereotype.Component
public class IdentityClock {
    public java.time.Instant now(){return java.time.Instant.now();}
}
