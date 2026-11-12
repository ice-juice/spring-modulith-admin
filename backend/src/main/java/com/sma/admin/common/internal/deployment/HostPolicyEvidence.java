package com.sma.admin.common.internal.deployment;

import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.internal.security.BrowserRequestProperties;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.security.TenantIngressProperties;
import com.sma.admin.common.internal.security.RequestSourceProperties;

@Component
public class HostPolicyEvidence implements DeploymentEvidenceSource {
    private final SchemaValidationConfiguration.Properties schema;
    private final BrowserRequestProperties browser;
    private final RequestGovernanceProperties requests;
    private final TenantIngressProperties ingress;
    private final DeploymentTrafficProperties traffic;
    private final RequestSourceProperties source;
    @org.springframework.beans.factory.annotation.Autowired
    public HostPolicyEvidence(SchemaValidationConfiguration.Properties schema, BrowserRequestProperties browser,
                              RequestGovernanceProperties requests, TenantIngressProperties ingress, DeploymentTrafficProperties traffic,RequestSourceProperties source) {
        this.schema=schema; this.browser=browser; this.requests=requests; this.ingress=ingress; this.traffic=traffic;this.source=source;
    }
    public HostPolicyEvidence(SchemaValidationConfiguration.Properties schema, BrowserRequestProperties browser,
                              RequestGovernanceProperties requests, TenantIngressProperties ingress, DeploymentTrafficProperties traffic) {
        this(schema,browser,requests,ingress,traffic,new RequestSourceProperties(null,null,null));
    }
    public HostPolicyEvidence(SchemaValidationConfiguration.Properties schema, BrowserRequestProperties browser,
                              RequestGovernanceProperties requests, TenantIngressProperties ingress) {
        this(schema,browser,requests,ingress,new DeploymentTrafficProperties(false));
    }
    @Override public String id() { return "common.host-policy"; }
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge) {
        DeploymentEvidence.requireHost();
        return new DeploymentFields().text("protocol","1").enumeration("schema-mode",schema.schemaMode())
                .flag("traffic-enabled",traffic.enabled())
                .values("source-proxies",source.trustedProxies()).number("source-hops",source.maximumHops())
                .number("source-header-characters",source.maximumHeaderCharacters())
                .values("browser-origins",browser.allowedOrigins()).values("trusted-proxies",ingress.trustedProxyAddresses())
                .number("browser-json-bytes",browser.maximumJsonBytes()).number("browser-concurrent",browser.maximumConcurrentJson())
                .number("browser-anonymous-concurrent",browser.maximumConcurrentAnonymousJson())
                .number("request-records",requests.maxRecordsPerTenant()).number("request-concurrent",requests.maxConcurrent())
                .duration("request-write-interval",requests.minimumWriteInterval()).proof(id(),challenge);
    }
}
