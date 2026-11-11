package com.sma.admin.identity.internal.service;

import java.net.IDN;
import java.util.Locale;
import com.sma.admin.common.BizException;
import com.sma.admin.identity.IdentityErrorCode;

/** Conservative mailbox identity: case-sensitive ASCII local part, IDNA/lowercase DNS domain; no provider rewrites. */
public final class IdentityEmailAddress {
    private IdentityEmailAddress(){}
    public static String normalize(String address){
        if(address==null || address.length()>320 || !address.equals(address.strip()))throw invalid();
        int at=address.lastIndexOf('@');if(at<1 || at!=address.indexOf('@'))throw invalid();
        String local=address.substring(0,at),domain;
        if(local.length()>64 || !local.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"))throw invalid();
        try{domain=IDN.toASCII(address.substring(at+1),IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);}
        catch(IllegalArgumentException malformed){throw invalid();}
        // Matches the current SMTP envelope contract: ASCII TLD, internationalized non-TLD labels are punycoded.
        if(domain.length()>253 || !domain.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}"))throw invalid();
        String result=local+"@"+domain;if(result.length()>254)throw invalid();return result;
    }
    private static BizException invalid(){return new BizException(IdentityErrorCode.INVALID_EMAIL);}
}
