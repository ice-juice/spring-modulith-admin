package com.sma.admin.rule.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class RuleCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(new CacheDescriptor("rule.runtime","rule","rule.cache.runtime","rule-runtime","compiled-rules",CacheDescriptor.Kind.RUNTIME,CacheDescriptor.Scope.TENANT,1,null,Set.of(CacheDescriptor.Operation.CLEAR),Set.of("idle-maintenance","process-stop"),"rule:maintenance:clean"));}
}
