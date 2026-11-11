package com.sma.admin.tenant.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

final class DomainDigests {
    private DomainDigests() {}
    static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
