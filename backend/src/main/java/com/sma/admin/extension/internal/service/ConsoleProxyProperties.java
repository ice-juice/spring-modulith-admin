package com.sma.admin.extension.internal.service;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.extension.protocol.Checks;

@ConfigurationProperties("sma.extension.console")
public record ConsoleProxyProperties(boolean enabled,URI securityUrl,Path tokenFile) {
    public ConsoleProxyProperties {
        if(enabled)Checks.require(securityUrl!=null && "https".equalsIgnoreCase(securityUrl.getScheme()) && securityUrl.getHost()!=null
                && securityUrl.getUserInfo()==null && securityUrl.getQuery()==null && securityUrl.getFragment()==null
                && (securityUrl.getPath().isEmpty() || securityUrl.getPath().equals("/")) && tokenFile!=null,"console-https-configuration-required");
    }
    @Override public String toString(){return "ConsoleProxyProperties[enabled="+enabled+",credentials=redacted]";}
}
