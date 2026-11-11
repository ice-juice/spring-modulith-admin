package com.sma.extension.protocol;

import java.util.Locale;

/** Archive pathname checks shared by the recursive immutable snapshot reader. */
public final class ArchiveInspector {
    private ArchiveInspector() {}
    public static void validatePath(String name) {
        Checks.require(name != null && !name.isEmpty() && name.length() <= 240 && !name.startsWith("/") && !name.contains("\\")
                && !name.contains(":") && !name.contains("\u0000"), "unsafe-archive-path");
        for (String part : name.split("/", -1)) {
            Checks.require(!part.equals(".") && !part.equals("..") && !part.endsWith(".") && !part.endsWith(" ")
                    && part.codePoints().noneMatch(Character::isISOControl), "unsafe-archive-path");
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            Checks.require(!base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"), "unsafe-archive-path");
        }
        Checks.require(!name.contains("//"), "unsafe-archive-path");
    }
}
