package com.sma.admin.common.internal.cache;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;
import static com.sma.admin.common.cache.CacheNodeSelectionRejected.Reason.*;

@Service
public class CacheNodeSelectionService implements CacheNodeSelection {
    private final CacheNodeIdentity identity;
    public CacheNodeSelectionService(CacheNodeIdentity identity){this.identity=identity;}
    public Selected select(String nodeId,String processId){
        if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache view permission required");
        if(nodeId!=null||processId!=null){
            if(nodeId==null||!nodeId.matches("[a-z][a-z0-9-]{0,47}")||processId==null
                    ||!processId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    ||!identity.properties().expected().contains(nodeId))throw new CacheNodeSelectionRejected(INVALID);
            if(!identity.properties().id().equals(nodeId))throw new CacheNodeSelectionRejected(WRONG_NODE);
            if(!identity.processId().equals(processId))throw new CacheNodeSelectionRejected(REPLACED_PROCESS);
        }
        return new Selected(identity.properties().id(),identity.processId());
    }
}
