package com.sma.admin.identity.internal.service;

import java.util.*;
import com.sma.admin.common.security.ActorRef;
import com.sma.admin.identity.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class SubjectAccessPolicies {
    private final Map<String,SubjectAccessPolicy> policies;
    public SubjectAccessPolicies(List<SubjectAccessPolicy> implementations){
        var mapped=new HashMap<String,SubjectAccessPolicy>();
        for(var policy:implementations){String application=policy.application();
            if(application==null || !application.matches("[a-z][a-z0-9.-]{0,63}"))throw new IllegalArgumentException("Invalid subject policy application");
            if(mapped.putIfAbsent(application,policy)!=null)throw new IllegalStateException("Duplicate subject access policy");
        }
        policies=Map.copyOf(mapped);
    }
    public void require(ActorRef actor,SubjectReadRequest request,SubjectAccessPolicy.Content content){
        var policy=policies.get(request.application());
        if(policy==null || !policy.canRead(actor,request,content))throw new AccessDeniedException("Subject access denied");
    }
}
