package com.sma.admin.common.internal.cache;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.CacheResourceAdapter;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.persistence.DatabaseTelemetry;
import com.sma.admin.common.security.CurrentUser;

@Component
public class SqlCacheAdapter implements CacheResourceAdapter {
    private final DatabaseTelemetry telemetry;
    public SqlCacheAdapter(DatabaseTelemetry telemetry){this.telemetry=telemetry;}
    public String region(){return "common.sql-statistics";}
    public Snapshot inspect(){
        if(!CurrentUser.require().permissions().contains("monitor:sql:view"))throw new AccessDeniedException("SQL statistics view denied");
        return new Snapshot(telemetry.statements().size(),10000,-1,0,"common.cache.lifecycle.sql-statistics");
    }
    public void authorize(CacheDescriptor.Operation operation){
        var permissions=CurrentUser.require().permissions();
        if(operation!=CacheDescriptor.Operation.RESET_STATS||!permissions.contains("monitor:sql:view")||!permissions.contains("monitor:sql:reset"))throw new AccessDeniedException("SQL statistics reset denied");
    }
    public Outcome execute(CacheDescriptor.Operation operation){authorize(operation);long before=telemetry.statements().size();telemetry.reset();return new Outcome(before,telemetry.statements().size());}
}
