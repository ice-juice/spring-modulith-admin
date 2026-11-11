package com.sma.admin.monitor.internal.service;

import java.util.List;
import java.util.Set;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.CacheTasks;

/** Native task protocol, deliberately not GovernedApi: submission already owns its RequestExecution receipt. */
@Service
public class CacheTaskService {
    private final CacheTasks tasks;
    public CacheTaskService(CacheTasks tasks){this.tasks=tasks;}
    public record PreviewInput(@NotNull CacheTasks.Action action,@Size(max=128) Set<String> regions,
            @Size(max=80) String owner,@Size(max=80) String group,@Size(max=80) String scenario,@NotBlank @Size(max=256) String reason) {}
    public CacheTasks.View preview(PreviewInput input){return tasks.preview(input.action(),new CacheTasks.Selection(input.regions(),input.owner(),input.group(),input.scenario()),input.reason());}
    public CacheTasks.View submit(String id,String key){return tasks.submit(id,key);}
    public CacheTasks.View get(String id){return tasks.get(id);}
    public com.sma.admin.common.cache.CacheNodeScope nodeScope(String id){return tasks.nodeScope(id);}
    public List<CacheTasks.View> recent(int limit){return tasks.recent(limit);}
    public CacheTasks.View cancel(String id){return tasks.cancel(id);}
}
