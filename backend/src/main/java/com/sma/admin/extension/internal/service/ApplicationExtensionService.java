package com.sma.admin.extension.internal.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.sma.admin.common.extension.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.extension.protocol.Checks;

@Service
public class ApplicationExtensionService implements ExtensionApplicationGateway {
    private final ExtensionRuntimeService runtime;
    private final Map<String,ExtensionApplicationPolicy> policies;
    public ApplicationExtensionService(ExtensionRuntimeService runtime,List<ExtensionApplicationPolicy> policies){this.runtime=runtime;this.policies=policies.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExtensionApplicationPolicy::pluginId,p -> p));}
    public Result invoke(String deployment,String plugin,String entry,String workKey,byte[] input) {
        var actor=CurrentUser.optional().filter(p -> p.machine()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Application identity required"));
        var policy=policies.get(plugin);if(policy==null)throw new org.springframework.security.access.AccessDeniedException("Application plugin policy required");
        var permissions=policy.permissions(actor.username().substring(4),actor.tenantId(),actor.portal());
        if(permissions==null || permissions.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Application plugin grant required");
        return WorkCaller.application(permissions,() -> {
            if(workKey==null)return new Result(null,"SUCCEEDED",null,null,runtime.invokeScoped(deployment,plugin,entry,input),null);
            var result=runtime.invokeWork(deployment,plugin,entry,workKey,input);
            return new Result(Long.toString(result.id()),result.state(),Long.toString(result.releaseRevision()),result.bundleId(),result.result(),result.failure());
        });
    }
}
