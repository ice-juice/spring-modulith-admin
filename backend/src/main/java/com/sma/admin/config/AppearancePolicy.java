package com.sma.admin.config;

/** Host extension for the current trusted tenant/portal's published presentation policy. No caller supplied tenant IDs. */
public interface AppearancePolicy {
    Decision current();
    record Decision(String defaultSkin,boolean allowPersonalSkin) {
        public Decision { if(!java.util.Set.of("iris","celadon","amber").contains(defaultSkin)) throw new IllegalArgumentException("Unsupported skin"); }
        public static Decision defaults() { return new Decision("iris",true); }
    }
}
