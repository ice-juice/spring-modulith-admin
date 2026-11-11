package com.sma.admin.common.internal.cache;

import java.util.concurrent.atomic.LongAdder;

/** Replace the whole window on reset. In-flight calls retain their old window and cannot contaminate the new one. */
final class CacheCounters {
    final long epoch;
    final LongAdder hits=new LongAdder(), misses=new LongAdder(), bypasses=new LongAdder(), rejected=new LongAdder();
    CacheCounters(long epoch){this.epoch=epoch;}
}
