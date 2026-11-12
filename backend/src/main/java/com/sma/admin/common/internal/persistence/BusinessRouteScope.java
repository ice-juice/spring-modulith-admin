package com.sma.admin.common.internal.persistence;

import java.util.function.Supplier;
import javax.sql.DataSource;

/** A pinned physical source is visible only during one business session operation. */
public final class BusinessRouteScope {
    private static final ThreadLocal<DataSource> CURRENT=new ThreadLocal<>();
    private BusinessRouteScope() {}
    public static boolean active(){return CURRENT.get()!=null;}

    public static DataSource require() {
        var source=CURRENT.get();
        if (source==null) throw new IllegalStateException("Business mapper used without a pinned storage session");
        return source;
    }
    public static <T> T within(DataSource source, Supplier<T> action) {
        if (source==null || action==null) throw new IllegalArgumentException("Business resource operation is missing");
        var previous=CURRENT.get();
        if (previous!=null && previous!=source)
            throw new IllegalStateException("Nested business mapper operations cannot change resources");
        CURRENT.set(source);
        try { return action.get(); }
        finally { if (previous==null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
