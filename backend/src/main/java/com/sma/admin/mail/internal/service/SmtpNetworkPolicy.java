package com.sma.admin.mail.internal.service;

import java.io.IOException;
import java.net.*;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @EnableConfigurationProperties(SmtpProperties.class)
public class SmtpNetworkPolicy {
    private final SmtpProperties settings;
    public SmtpNetworkPolicy(SmtpProperties settings){this.settings=settings;}
    /** Resolve once and pin the connection to the validated result, including when DNS changes afterward. */
    public InetAddress authorize(String host,int port)throws IOException{
        var route=settings.routes().stream().filter(r->r.host().equals(host)&&r.ports().contains(port)).findFirst()
                .orElseThrow(()->new PolicyDenied());
        InetAddress[] addresses=resolve(host);
        if(addresses.length==0 || addresses.length>32)throw new PolicyDenied();
        for(var address:addresses) {
            if(reserved(address) && !route.approvedPrivateAddresses().contains(address.getHostAddress()))throw new PolicyDenied();
            // Never authorize metadata or multicast/unspecified destinations, even via the private override.
            if(address.isAnyLocalAddress() || address.isMulticastAddress() || address.isLinkLocalAddress())throw new PolicyDenied();
        }
        return addresses[0];
    }
    protected InetAddress[] resolve(String host)throws UnknownHostException{return InetAddress.getAllByName(host);}
    static boolean reserved(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return true;
        byte[] b=address.getAddress();int first=Byte.toUnsignedInt(b[0]);
        if(b.length==4){int second=Byte.toUnsignedInt(b[1]);return first==0 || first>=224 || first==127 || first==10
                || (first==100&&second>=64&&second<=127) || (first==169&&second==254)
                || (first==172&&second>=16&&second<=31) || (first==192&&second==168)
                || (first==192&&second==0) || (first==198&&(second==18||second==19));}
        // Global unicast only; reject ULA, mapped/special ranges and transition prefixes by default.
        return (first&0xe0)!=0x20 || (first==0x20 && b[1]==0x02)
                || (first==0x20 && b[1]==0x01 && b[2]==0 && b[3]==0);
    }
    public static class PolicyDenied extends IOException {
        public PolicyDenied(){super("SMTP destination not approved");}
    }
}
