package com.sma.business.shopdemo.internal;

import java.time.Duration;
import java.util.*;
import com.sma.admin.common.cache.*;

@org.springframework.stereotype.Component
public class ShopCacheCatalog implements BusinessCacheCatalog {
    @Override public Collection<Region> regions(){return List.of(new Region("shopdemo",new CacheDescriptor(
            "shopdemo.orders","shopdemo","shopdemo.cache.orders","orders","count",CacheDescriptor.Kind.DATA,
            CacheDescriptor.Scope.TENANT,1,new CachePolicy(Duration.ofSeconds(30),Duration.ZERO,100,65536,4096),
            Set.of(CacheDescriptor.Operation.CLEAR),Set.of("order-created"),"shopdemo:order:view")));}
}
