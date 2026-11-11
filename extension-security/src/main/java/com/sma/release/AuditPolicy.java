package com.sma.release;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.extension.protocol.Checks;

@ConfigurationProperties("release.audit")
public record AuditPolicy(boolean required,int maximumLagSeconds,int maximumUnmirroredEntries) {
    public AuditPolicy {if(maximumLagSeconds==0)maximumLagSeconds=120;if(maximumUnmirroredEntries==0)maximumUnmirroredEntries=100;
        Checks.require(maximumLagSeconds>=30 && maximumLagSeconds<=900 && maximumUnmirroredEntries>=1 && maximumUnmirroredEntries<=1000,"invalid-audit-policy");}
}
