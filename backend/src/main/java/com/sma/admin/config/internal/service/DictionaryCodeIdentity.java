package com.sma.admin.config.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact Unicode scalar strings. Invalid surrogates must not become UTF-8 replacement aliases. */
final class DictionaryCodeIdentity {
    private DictionaryCodeIdentity() {}
    static void validate(String code) {
        if(code==null || code.isBlank() || code.length()>128 || code.chars().anyMatch(Character::isISOControl)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(code)) throw new IllegalArgumentException("Invalid dictionary code");
    }
    static String digest(String code) {
        validate(code);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
