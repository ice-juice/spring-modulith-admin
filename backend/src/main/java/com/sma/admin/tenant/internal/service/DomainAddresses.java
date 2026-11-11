package com.sma.admin.tenant.internal.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Parses IP literals only. A deployment allow-list entry must never trigger another DNS lookup. */
public final class DomainAddresses {
    private DomainAddresses() {}
    public static InetAddress literal(String value) {
        if(value==null || value.isBlank() || value.indexOf('%')>=0) throw new IllegalArgumentException("IP literal required");
        if(value.indexOf(':')<0) {
            var parts=value.split("\\.",-1);if(parts.length!=4) throw new IllegalArgumentException("IPv4 literal required");
            byte[] bytes=new byte[4];
            for(int i=0;i<4;i++) {
                if(!parts[i].matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(parts[i])>255) throw new IllegalArgumentException("Invalid IPv4 literal");
                bytes[i]=(byte)Integer.parseInt(parts[i]);
            }
            try { return InetAddress.getByAddress(bytes); } catch(UnknownHostException impossible) { throw new IllegalStateException(impossible); }
        }
        if(!value.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("IPv6 literal required");
        try { return InetAddress.getByName(value); } // The colon and character whitelist prohibit hostname resolution.
        catch(UnknownHostException invalid) { throw new IllegalArgumentException("Invalid IPv6 literal",invalid); }
    }
}
