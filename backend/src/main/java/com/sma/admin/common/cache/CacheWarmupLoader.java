package com.sma.admin.common.cache;

import java.time.Duration;
import java.util.List;

/** Trusted module SPI: bounded read-only enumeration and idempotent cache reads. No business writes or external side effects.
 * Cursors/keys remain internal; they are never accepted from a governance HTTP request or exposed in task results.
 * Each call must obey a finite I/O timeout. The host checks cancellation, authority and its deadline between calls.
 */
public interface CacheWarmupLoader {
    record Limits(int pageSize,int maxItems,int itemsPerSecond,Duration maxDuration) {
        public Limits {
            if(pageSize<1||pageSize>16||maxItems<pageSize||maxItems>1000||itemsPerSecond<1||itemsPerSecond>20
                    ||maxDuration==null||maxDuration.compareTo(Duration.ofSeconds(1))<0||maxDuration.compareTo(Duration.ofMinutes(2))>0
                    ||maxDuration.getNano()!=0)throw new IllegalArgumentException("Invalid cache warmup budget");
        }
    }
    record Spec(String region,int version,String readPermission,Limits limits) {
        public Spec {
            if(region==null||!region.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}")||version<1||limits==null
                    ||readPermission==null||!readPermission.matches("[a-z]+(:[a-z][a-z0-9-]*){1,3}")
                    ||!readPermission.startsWith(region.substring(0,region.indexOf('.'))+":"))
                throw new IllegalArgumentException("Warmup requires its owning module read permission");
        }
    }
    record Page(List<String> keys,String nextCursor,boolean complete) {
        public Page {
            keys=List.copyOf(keys);
            if(keys.size()>16||keys.stream().anyMatch(key->key.isBlank()||key.length()>128)
                    ||nextCursor==null||!nextCursor.matches("[\\x21-\\x7e]{0,128}")||(!complete&&(keys.isEmpty()||nextCursor.isBlank())))
                throw new IllegalArgumentException("Invalid cache warmup page");
        }
    }
    Spec specification();
    /** Use a stable keyset cursor, a short read transaction, current tenant and original actor's data scope. */
    Page enumerate(String cursor,int limit);
    /** Called outside any database transaction, so ManagedCache can populate. Successful read is not proof of retention. */
    void load(String key);
}
