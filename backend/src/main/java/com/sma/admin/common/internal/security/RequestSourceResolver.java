package com.sma.admin.common.internal.security;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import com.sma.admin.common.security.RequestSource;
import static com.sma.admin.common.security.RequestSource.Basis.*;

final class RequestSourceResolver {
    private final RequestSourceProperties properties;
    private final List<SourceIp.Network> networks;
    RequestSourceResolver(RequestSourceProperties properties){
        this.properties=properties;networks=properties.trustedProxies().stream().map(SourceIp::network).toList();
    }
    RequestSource resolve(HttpServletRequest request){
        final SourceIp peer;
        try{peer=SourceIp.parse(request.getRemoteAddr());}catch(IllegalArgumentException invalid){return new RequestSource("","",UNKNOWN_PEER,0);}
        boolean forwarded=request.getHeader("Forwarded")!=null;
        String header=request.getHeader("X-Forwarded-For");
        if(!trusted(peer))return direct(peer,forwarded || header!=null?UNTRUSTED_FORWARDING:DIRECT);
        if(forwarded)return direct(peer,INVALID_CHAIN); // One supported protocol; mixed/unsupported sources never merge.
        if(header==null)return direct(peer,MISSING_CHAIN);
        var all=request.getHeaders("X-Forwarded-For");
        if(all!=null){if(all.hasMoreElements())all.nextElement();if(all.hasMoreElements())return direct(peer,INVALID_CHAIN);}
        if(header.length()>properties.maximumHeaderCharacters())return direct(peer,INVALID_CHAIN);
        var addresses=new ArrayList<SourceIp>();int start=0;
        try{
            for(int i=0;i<=header.length();i++)if(i==header.length() || header.charAt(i)==','){
                if(addresses.size()>=properties.maximumHops())return direct(peer,INVALID_CHAIN);
                String part=header.substring(start,i);
                // OWS only; reject controls rather than accepting String.trim's CR/LF removal.
                if(part.chars().anyMatch(c->c<32 && c!='\t' || c>126))return direct(peer,INVALID_CHAIN);
                addresses.add(SourceIp.parse(part.replaceAll("^[ \\t]+|[ \\t]+$","")));start=i+1;
            }
        }catch(IllegalArgumentException malformed){return direct(peer,INVALID_CHAIN);}
        int hops=0;SourceIp candidate=peer;
        for(int i=addresses.size()-1;i>=0 && trusted(candidate);i--){candidate=addresses.get(i);hops++;}
        if(trusted(candidate))return direct(peer,INVALID_CHAIN); // No observable untrusted origin, never guess.
        return new RequestSource(peer.text(),candidate.text(),TRUSTED_CHAIN,hops);
    }
    private boolean trusted(SourceIp address){return networks.stream().anyMatch(network->network.contains(address));}
    private RequestSource direct(SourceIp peer,RequestSource.Basis basis){return new RequestSource(peer.text(),peer.text(),basis,0);}
}
