package com.sma.admin.common.security;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Syntax policy for browser destinations, not an SSRF/network authorization policy. */
public final class WebAddresses {
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\\s\\\\]|%(?:0[0-9a-f]|1[0-9a-f]|5c|7f)", Pattern.CASE_INSENSITIVE);
    private WebAddresses() {}

    public static Optional<String> localResource(String value) {
        if (!syntax(value) || !value.startsWith("/") || value.startsWith("//")) return Optional.empty();
        try {
            var uri = URI.create(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || !uri.normalize().equals(uri)) return Optional.empty();
            return Optional.of(value);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    public static Optional<String> httpLink(String value) {
        if (!syntax(value)) return Optional.empty();
        try {
            var uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) return Optional.empty();
            return Optional.of(value);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    public static Optional<String> origin(String value) {
        return httpLink(value).filter(link -> {
            var uri = URI.create(link);
            return (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null && uri.getRawFragment() == null;
        }).map(link -> {
            var uri = URI.create(link);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (port == -1 || (port == 443 && scheme.equals("https")) || (port == 80 && scheme.equals("http")) ? "" : ":" + port);
        });
    }
    private static boolean syntax(String value) {
        return value != null && !value.isBlank() && value.length() <= 2048 && !UNSAFE.matcher(value).find();
    }
}
