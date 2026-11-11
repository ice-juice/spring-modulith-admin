package com.sma.admin.common.internal.security;

import java.net.InetAddress;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.admin.common.security.TenantHost;

@ConfigurationProperties("sma.tenant.entry")
public record TenantIngressProperties(Set<String> trustedProxyAddresses) {
    public TenantIngressProperties {
        trustedProxyAddresses=trustedProxyAddresses==null?Set.of():trustedProxyAddresses.stream()
                .map(TenantIngressProperties::literalAddress).collect(Collectors.toUnmodifiableSet());
        if(trustedProxyAddresses.size()>64) throw new IllegalArgumentException("Too many trusted proxy addresses");
    }
    public boolean trusts(String address) {
        try { return trustedProxyAddresses.contains(literalAddress(address)); }
        catch(IllegalArgumentException invalid) { return false; }
    }
    private static String literalAddress(String address) {
        if(address==null || address.length()>64 || !address.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("Proxy must be an IP literal");
        if(!address.contains(":")) {
            if(!address.matches("[0-9]+(?:\\.[0-9]+){3}")) throw new IllegalArgumentException("Proxy must be an IP literal");
            return TenantHost.domain(address);
        }
        try { return InetAddress.getByName(address).getHostAddress(); } // Colon plus literal-only validation prevents DNS lookups.
        catch(java.net.UnknownHostException invalid) { throw new IllegalArgumentException("Invalid proxy IP literal"); }
    }
}
