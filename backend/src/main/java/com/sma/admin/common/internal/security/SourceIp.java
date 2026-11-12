package com.sma.admin.common.internal.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/** Strict numeric literals only. No hostname, zone, bracket or port parsing, and no DNS. */
final class SourceIp {
    private final byte[] bytes;
    private SourceIp(byte[] bytes){this.bytes=bytes.clone();}
    static SourceIp parse(String value){
        if(value==null || value.isEmpty() || value.length()>64 || !value.matches("[0-9A-Fa-f:.]+"))throw invalid();
        if(!value.contains(":")){
            var parts=value.split("\\.",-1);if(parts.length!=4)throw invalid();byte[] bytes=new byte[4];
            for(int i=0;i<4;i++){
                if(!parts[i].matches("0|[1-9][0-9]{0,2}"))throw invalid();
                int octet=Integer.parseInt(parts[i]);if(octet>255)throw invalid();bytes[i]=(byte)octet;
            }
            return new SourceIp(bytes);
        }
        // InetAddress maps ::ffff:x.y.z.w to IPv4. Normalize both peer and CIDR consistently.
        if(value.contains("."))parse(value.substring(value.lastIndexOf(':')+1));
        try{return new SourceIp(InetAddress.getByName(value).getAddress());}
        catch(UnknownHostException malformed){throw invalid();}
    }
    String text(){try{return InetAddress.getByAddress(bytes).getHostAddress();}catch(UnknownHostException impossible){throw new IllegalStateException(impossible);}}
    static Network network(String value){
        if(value==null || value.length()>68)throw invalid();
        var parts=value.split("/",-1);if(parts.length>2)throw invalid();var ip=parse(parts[0]);int bits=ip.bytes.length*8;
        int prefix=bits;
        if(parts.length==2){
            if(!parts[1].matches("[1-9][0-9]{0,2}"))throw invalid();prefix=Integer.parseInt(parts[1]);
            if(ip.bytes.length==4 && parts[0].contains(":")) { if(prefix<97 || prefix>128)throw invalid();prefix-=96; }
            if(prefix<1 || prefix>bits)throw invalid();
        }
        byte[] masked=ip.bytes.clone();for(int bit=prefix;bit<bits;bit++)masked[bit/8]&=(byte)~(1<<(7-bit%8));
        // Do not silently enlarge a mistakenly configured host/prefix into a network.
        if(!Arrays.equals(masked,ip.bytes))throw invalid();
        return new Network(new SourceIp(masked),prefix);
    }
    record Network(SourceIp address,int prefix){
        boolean contains(SourceIp other){
            if(other.bytes.length!=address.bytes.length)return false;
            for(int bit=0;bit<prefix;bit++)if(((address.bytes[bit/8]^other.bytes[bit/8])&(1<<(7-bit%8)))!=0)return false;
            return true;
        }
        String canonical(){return address.text()+"/"+prefix;}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Expected a bounded numeric IP or network");}
}
