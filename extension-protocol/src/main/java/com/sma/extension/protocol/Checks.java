package com.sma.extension.protocol;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Checks {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9.-]{0,79}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private Checks() {}
    public static String id(String value) { require(value != null && ID.matcher(value).matches(), "invalid-id"); return value; }
    public static String hash(String value) { require(value != null && HASH.matcher(value).matches(), "invalid-hash"); return value; }
    public static String text(String value, int max) {
        require(value != null && !value.isBlank() && value.length() <= max && value.codePoints().noneMatch(Character::isISOControl), "invalid-text");
        return value;
    }
    public static void require(boolean value, String code) { if (!value) throw new ProtocolException(code); }
    public static void unique(Collection<?> values, int max) {
        Objects.requireNonNull(values);
        require(values.size() <= max && values.stream().allMatch(Objects::nonNull)
                && values.stream().distinct().count() == values.size(), "invalid-collection");
    }
}
