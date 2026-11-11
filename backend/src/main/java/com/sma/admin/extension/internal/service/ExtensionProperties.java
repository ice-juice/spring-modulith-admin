package com.sma.admin.extension.internal.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.extension.protocol.Checks;

@ConfigurationProperties("sma.extension")
public record ExtensionProperties(boolean enabled, URI securityUrl, String nodeId, String nodeToken,
        String environment, String issuer, String audience, Path trustFile, Path cacheDirectory, List<String> deployments,Path nodeTokenFile) {
    public ExtensionProperties {
        deployments=deployments==null?List.of():List.copyOf(deployments);
        if(enabled) {
            Checks.require(securityUrl!=null && "https".equalsIgnoreCase(securityUrl.getScheme()) && securityUrl.getHost()!=null
                    && securityUrl.getUserInfo()==null && securityUrl.getQuery()==null && securityUrl.getFragment()==null,"extension-https-endpoint-required");
            Checks.id(nodeId);Checks.id(environment);Checks.id(issuer);Checks.id(audience);
            Checks.require((nodeTokenFile!=null || nodeToken!=null && nodeToken.length()>=32) && trustFile!=null && cacheDirectory!=null && !deployments.isEmpty(),"extension-configuration-incomplete");
            Checks.require(nodeTokenFile==null || nodeToken==null || nodeToken.isBlank(),"choose-one-node-token-source");
            deployments.forEach(Checks::id);
        }
    }
    @Override public String toString() { return "ExtensionProperties[enabled="+enabled+", secrets=redacted]"; }
}
