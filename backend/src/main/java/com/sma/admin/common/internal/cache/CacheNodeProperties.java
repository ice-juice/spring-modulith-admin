package com.sma.admin.common.internal.cache;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.cache.nodes")
public record CacheNodeProperties(String cluster,String id,List<String> expected,Boolean enabled,Long version) {
    public CacheNodeProperties(String cluster,String id,List<String> expected,Boolean enabled){this(cluster,id,expected,enabled,null);}
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public CacheNodeProperties {
        cluster=cluster==null?"sma":cluster;id=id==null?"local":id;enabled=enabled==null?true:enabled;
        version=version==null?1L:version;
        expected=expected==null?List.of(id):List.copyOf(expected);
        if(!cluster.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")||!id.matches("[a-z][a-z0-9-]{0,47}")
                ||version<1||expected.isEmpty()||expected.size()>32||new HashSet<>(expected).size()!=expected.size()||!expected.contains(id)
                ||expected.stream().anyMatch(node->!node.matches("[a-z][a-z0-9-]{0,47}")))
            throw new IllegalArgumentException("Invalid cache node manifest");
        expected=expected.stream().sorted().toList();
    }
}
