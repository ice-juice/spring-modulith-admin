package com.sma.admin.common.security;

/** Network observation, never an identity, tenant binding or authorization decision. No raw forwarding headers. */
public record RequestSource(String peerAddress, String clientAddress, Basis basis, int trustedHops) {
    public enum Basis { DIRECT, UNTRUSTED_FORWARDING, TRUSTED_CHAIN, MISSING_CHAIN, INVALID_CHAIN, UNKNOWN_PEER }
    public RequestSource {
        if(peerAddress==null || clientAddress==null || basis==null || peerAddress.length()>64 || clientAddress.length()>64
                || !peerAddress.matches("[0-9a-f:.]*") || !clientAddress.matches("[0-9a-f:.]*") || trustedHops<0 || trustedHops>32
                || basis!=Basis.TRUSTED_CHAIN && trustedHops!=0)
            throw new IllegalArgumentException("Invalid network observation");
    }
}
