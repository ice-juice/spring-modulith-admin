package com.sma.admin.common.internal.cache;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.cache.CachePolicy;
import com.sma.admin.common.deployment.*;

@Component
public class CachePolicyEvidence implements DeploymentEvidenceSource {
    private final CacheProperties settings;
    private final CacheRegistry registry;
    private final CacheResilienceProperties resilience;
    private final CacheWarmupProperties warmup;
    private final CacheResourceTaskProperties resources;
    private final ObjectProvider<CacheRedisProperties> redis;
    private final BusinessCacheRegistry business;
    public CachePolicyEvidence(CacheProperties settings, CacheRegistry registry, CacheResilienceProperties resilience,
            CacheWarmupProperties warmup, CacheResourceTaskProperties resources, ObjectProvider<CacheRedisProperties> redis) {
        this(settings,registry,resilience,warmup,resources,redis,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CachePolicyEvidence(CacheProperties settings, CacheRegistry registry, CacheResilienceProperties resilience,
            CacheWarmupProperties warmup, CacheResourceTaskProperties resources, ObjectProvider<CacheRedisProperties> redis,
            BusinessCacheRegistry business) {
        this.settings=settings;this.registry=registry;this.resilience=resilience;this.warmup=warmup;this.resources=resources;this.redis=redis;
        this.business=business;
    }
    @Override public String id() { return "common.cache-policy"; }
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge) {
        DeploymentEvidence.requireHost();
        var fields=new DeploymentFields().text("protocol","1").enumeration("provider",settings.provider())
                .text("namespace",settings.namespace()).enumeration("deployment-mode",settings.deploymentMode())
                .number("maximum-bytes",settings.maxBytes()).number("maximum-scopes",settings.maxScopes())
                .text("ttl-jitter",Double.toString(settings.ttlJitter())).flag("warmup",warmup.enabled()).flag("native-resources",resources.enabled())
                .number("concurrent-loads",resilience.maxConcurrentLoads()).number("concurrent-per-region",resilience.maxConcurrentPerRegion())
                .duration("lock-wait",resilience.lockWait()).number("failure-threshold",resilience.failureThreshold()).duration("retry-delay",resilience.retryDelay());
        var regions=registry.regions().stream().sorted(java.util.Comparator.comparing(CacheDescriptor::id)).toList();
        fields.number("region-count",regions.size());
        for(int i=0;i<regions.size();i++){
            var region=regions.get(i);
            var declaration=new DeploymentFields().text("id",region.id()).text("owner",region.owner()).text("name-key",region.nameKey())
                    .text("group",region.group()).text("scenario",region.scenario()).enumeration("kind",region.kind()).enumeration("scope",region.scope())
                    .number("schema-version",region.schemaVersion()).text("permission",region.managePermission())
                    .values("operations",region.operations().stream().map(Enum::name).toList()).values("invalidation",region.invalidatedBy());
            if(region.policy()!=null) policy(declaration,"registered",region.policy());
            if(region.kind()==CacheDescriptor.Kind.DATA) policy(declaration,"effective",settings.policy(region.id(),region.policy()));
            fields.text("region."+i,declaration.proof("common.cache-region",challenge).digest());
        }
        var businessRegions=business==null?java.util.List.<com.sma.admin.common.cache.BusinessCacheCatalog.Region>of():
                business.regions().stream().sorted(java.util.Comparator.comparing(r->r.descriptor().id())).toList();
        fields.number("business.region-count",businessRegions.size()).number("business.maximum-sources",BusinessCacheService.MAX_SOURCES);
        for(int i=0;i<businessRegions.size();i++){
            var registration=businessRegions.get(i);var region=registration.descriptor();
            var declaration=new DeploymentFields().text("storage-group",registration.storageGroup()).text("id",region.id())
                    .text("owner",region.owner()).enumeration("scope",region.scope()).number("schema-version",region.schemaVersion())
                    .text("permission",region.managePermission()).values("invalidation",region.invalidatedBy());
            policy(declaration,"registered",region.policy());
            policy(declaration,"effective",settings.defaults()==null?region.policy():settings.defaults().apply(region.policy()));
            fields.text("business.region."+i,declaration.proof("common.business-cache-region",challenge).digest());
        }
        if(settings.provider()==CacheProperties.Provider.REDIS){
            var connection=redis.getIfAvailable(); if(connection==null)return DeploymentEvidence.unavailable(id());
            fields.number("redis.database",connection.database()).flag("redis.tls",connection.ssl().enabled())
                    .duration("redis.timeout",connection.timeout()).duration("redis.connect-timeout",connection.connectTimeout());
            if(connection.cluster()!=null){
                fields.text("redis.topology","cluster").number("redis.redirects",connection.cluster().maxRedirects())
                        .values("redis.endpoints",connection.cluster().nodes().stream().map(CachePolicyEvidence::endpoint).toList());
            }else if(connection.sentinel()!=null){
                fields.text("redis.topology","sentinel").text("redis.master",connection.sentinel().master())
                        .values("redis.endpoints",connection.sentinel().nodes().stream().map(CachePolicyEvidence::endpoint).toList());
            }else fields.text("redis.topology","standalone").text("redis.host",connection.host().toLowerCase(Locale.ROOT)).number("redis.port",connection.port());
            // ACL users/passwords can differ per node; connection usability remains the connection factory's responsibility.
        }
        return fields.proof(id(),challenge);
    }
    private static String endpoint(String value){var uri=URI.create("redis://"+value);return uri.getHost().toLowerCase(Locale.ROOT)+":"+uri.getPort();}
    private static void policy(DeploymentFields fields,String prefix,CachePolicy policy){
        fields.duration(prefix+".ttl",policy.ttl()).duration(prefix+".negative-ttl",policy.negativeTtl()).number(prefix+".entries",policy.maxEntries())
                .number(prefix+".bytes",policy.maxBytes()).number(prefix+".entry-bytes",policy.maxEntryBytes());
    }
}
