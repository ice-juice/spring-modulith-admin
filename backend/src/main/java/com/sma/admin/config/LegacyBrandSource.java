package com.sma.admin.config;

import java.util.Map;

/** Host upgrade contract, not a user-facing parameter API. Implementations own locking and archival in the caller's platform transaction. */
public interface LegacyBrandSource {
    enum Key {
        NAME("system.name"), COPYRIGHT_OWNER("system.copyrightCompany"), COPYRIGHT_URL("system.copyrightUrl"),
        ICP_RECORD("system.recordInfo"), ICP_URL("system.recordUrl"),
        BACKGROUND_URL("login.backgroundUrl"), BACKGROUND_BLUR("login.backgroundBlur");
        private final String parameter;
        Key(String parameter) { this.parameter=parameter; }
        public String parameter() { return parameter; }
    }
    /** A secret source must be redacted by the provider before it leaves Config. */
    record Value(String text,boolean publicReadable,boolean secret) {
        public Value { if(secret) text=null; }
        @Override public String toString() { return "LegacyBrandValue[publicReadable="+publicReadable+", secret="+secret+"]"; }
    }
    record Snapshot(String sourceRevision,Map<Key,Value> values) {
        public Snapshot { values=Map.copyOf(values); }
    }
    Snapshot snapshot();
    void archive(String expectedSourceRevision);
}
