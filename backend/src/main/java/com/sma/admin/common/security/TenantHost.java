package com.sma.admin.common.security;

import java.net.IDN;
import java.util.Locale;

/** Pure host normalization: never resolves DNS or accepts URL/userinfo/forwarded-header lists. */
public final class TenantHost {
    private TenantHost() {}

    public static String domain(String value) {
        if (value == null || value.isEmpty() || value.length() > 1024
                || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || value.chars().anyMatch(c -> "/\\@:#?%,;[]".indexOf(c) >= 0)) throw invalid();
        String result;
        try { result = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException badIdn) { throw invalid(); }
        if (result.endsWith(".")) result = result.substring(0, result.length()-1);
        if (result.isEmpty() || result.length() > 253) throw invalid();
        for (String label : result.split("\\.", -1)) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw invalid();
        }
        if (result.matches("[0-9.]+")) {
            String[] octets = result.split("\\.");
            if (octets.length != 4) throw invalid();
            for (String octet : octets) {
                if (octet.length() > 3 || (octet.length() > 1 && octet.startsWith("0")) || Integer.parseInt(octet) > 255) throw invalid();
            }
        }
        return result;
    }

    /** HTTP Host authority. Ports do not create a second tenant namespace; only loopback IPv6 is supported. */
    public static String authority(String value) {
        if (value == null || value.isEmpty()) throw invalid();
        if (value.startsWith("[")) {
            if (value.equals("[::1]")) return "[::1]";
            if (value.startsWith("[::1]:")) { port(value.substring(6)); return "[::1]"; }
            throw invalid();
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            port(value.substring(colon+1));
            return domain(value.substring(0,colon));
        }
        return domain(value);
    }

    private static void port(String value) {
        if (!value.matches("[0-9]{1,5}")) throw invalid();
        int number = Integer.parseInt(value);
        if (number < 1 || number > 65535) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid tenant host"); }
}
