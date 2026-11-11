package com.sma.admin.common.internal.persistence;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

/** IDs are opaque; the allocator uses neither wall clocks nor machine/process-derived worker numbers. */
public final class DatabaseIdentifierGenerator implements IdentifierGenerator {
    private final MySqlIdBlockSource source;
    private final ReentrantLock lock=new ReentrantLock(true);
    private long next,end;
    public DatabaseIdentifierGenerator(DataSource source){this(new MySqlIdBlockSource(source));}
    DatabaseIdentifierGenerator(MySqlIdBlockSource source){this.source=source;}
    @Override public Number nextId(Object entity){
        boolean acquired=false;
        try{
            acquired=lock.tryLock(2,TimeUnit.SECONDS);if(!acquired)throw MySqlIdBlockSource.unavailable();
            if(next==end){var block=source.reserve();next=block.first();end=block.exclusiveEnd();}
            return next++;
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw MySqlIdBlockSource.unavailable();}
        finally{if(acquired)lock.unlock();}
    }
}
