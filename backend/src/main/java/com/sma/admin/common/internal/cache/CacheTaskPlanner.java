package com.sma.admin.common.internal.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.CurrentUser;

/** Resolves only registered metadata; never enumerates storage keys or business data. */
@Component
public class CacheTaskPlanner {
    private final CacheRegistry registry;
    private final CacheProperties properties;
    private final CacheRevisionStore revisions;
    private final CacheWarmupRegistry warmups;
    private final CacheResourceRegistry resources;
    private final JsonMapper json=JsonMapper.builder().build();
    public CacheTaskPlanner(CacheRegistry registry,CacheProperties properties,CacheRevisionStore revisions){
        this(registry,properties,revisions,null);
    }
    public CacheTaskPlanner(CacheRegistry registry,CacheProperties properties,CacheRevisionStore revisions,CacheWarmupRegistry warmups){
        this(registry,properties,revisions,warmups,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheTaskPlanner(CacheRegistry registry,CacheProperties properties,CacheRevisionStore revisions,CacheWarmupRegistry warmups,CacheResourceRegistry resources){
        this.registry=registry;this.properties=properties;this.revisions=revisions;
        this.warmups=warmups;
        this.resources=resources;
    }
    public record Plan(List<CacheTasks.Target> targets,List<CacheTasks.Skipped> skipped){
        public Plan {targets=List.copyOf(targets);skipped=List.copyOf(skipped);}
    }
    public Plan preview(CacheTasks.Action action,CacheTasks.Selection selection){
        require(action);
        if(selection==null||selection.regions().size()>128)throw invalid();
        for(var id:selection.regions())if(id.length()>96||!id.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}"))throw invalid();
        for(var filter:Arrays.asList(selection.owner(),selection.group(),selection.scenario()))
            if(filter!=null&&!filter.isBlank()&&!filter.matches("[a-z][a-z0-9.-]{0,79}"))throw invalid();
        var known=new TreeMap<String,CacheDescriptor>();registry.regions().forEach(row->known.put(row.id(),row));
        var targets=new ArrayList<CacheTasks.Target>();var skipped=new ArrayList<CacheTasks.Skipped>();
        Collection<String> requested=selection.regions().isEmpty()?known.values().stream()
                .filter(row->matches(selection.owner(),row.owner())&&matches(selection.group(),row.group())&&matches(selection.scenario(),row.scenario()))
                .map(CacheDescriptor::id).toList():new TreeSet<>(selection.regions());
        // Bound the complete preview, including exclusions, rather than silently truncating targets.
        if(requested.size()>128)throw new CacheTaskRejected(CacheTaskRejected.Reason.LIMIT);
        for(var id:requested){
            var row=known.get(id);CacheTasks.SkipReason skip=null;
            if(row==null)skip=CacheTasks.SkipReason.UNKNOWN_REGION;
            else if(!matches(selection.owner(),row.owner())||!matches(selection.group(),row.group())||!matches(selection.scenario(),row.scenario())){
                if(selection.regions().isEmpty())continue;
                skip=CacheTasks.SkipReason.FILTERED_OUT;
            }else if(row.kind()==CacheDescriptor.Kind.PROTECTED)skip=CacheTasks.SkipReason.PROTECTED;
            else if(row.kind()!=CacheDescriptor.Kind.DATA){
                if(resources==null||row.kind()!=CacheDescriptor.Kind.RUNTIME)skip=CacheTasks.SkipReason.ADAPTER_REQUIRED;
                else {var capability=resources.capability(id,operation(action));if(!capability.available())skip=CacheTasks.SkipReason.valueOf(capability.reason());}
            }
            else if(!row.operations().contains(operation(action)))skip=CacheTasks.SkipReason.UNSUPPORTED;
            else if(!CurrentUser.require().permissions().contains(row.managePermission()))skip=CacheTasks.SkipReason.FORBIDDEN;
            if(skip==null&&action==CacheTasks.Action.PREWARM){
                var eligibility=warmups==null?null:warmups.inspect(id);
                if(eligibility==null)skip=CacheTasks.SkipReason.UNSUPPORTED;
                else if(!eligibility.available())skip=CacheTasks.SkipReason.valueOf(eligibility.reason());
            }
            if(skip!=null){skipped.add(new CacheTasks.Skipped(id,skip));continue;}
            targets.add(new CacheTasks.Target(id,row.nameKey(),row.scope().name(),row.managePermission(),
                    row.kind()==CacheDescriptor.Kind.DATA?revisions.state(id).controlVersion():0,fingerprint(row),action==CacheTasks.Action.PREWARM?warmups.specification(id):null,
                    row.kind()==CacheDescriptor.Kind.RUNTIME?resources.target(id):null));
        }
        return new Plan(targets,skipped);
    }
    /** Reauthorization is independent from expiry/version: an accepted request must remain replayable. */
    public void authorize(CacheTasks.Action action,List<CacheTasks.Target> targets){
        authorize(action,targets,false);
    }
    public void authorizeCancellation(CacheTasks.Action action,List<CacheTasks.Target> targets){authorize(action,targets,true);}
    private void authorize(CacheTasks.Action action,List<CacheTasks.Target> targets,boolean cancelling){
        require(action);
        for(var target:targets){
            var row=registry.regions().stream().filter(d->d.id().equals(target.region())).findFirst()
                    .orElseThrow(()->new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED));
            if((row.kind()!=CacheDescriptor.Kind.DATA&&row.kind()!=CacheDescriptor.Kind.RUNTIME)||!row.operations().contains(operation(action))
                    ||!CurrentUser.require().permissions().contains(row.managePermission())
                    ||!CurrentUser.require().permissions().contains(target.managePermission()))throw new AccessDeniedException("Cache task target denied");
            if(row.kind()==CacheDescriptor.Kind.RUNTIME){
                if(resources==null||target.resource()==null)throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
                if(!cancelling)resources.authorize(row.id(),operation(action));
            }
            if(action==CacheTasks.Action.PREWARM&&!cancelling){
                if(warmups==null||target.warmup()==null)throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
                var spec=warmups.specification(row.id());
                if(spec==null||!CurrentUser.require().permissions().contains(spec.readPermission())||!CurrentUser.require().permissions().contains(target.warmup().readPermission()))throw new AccessDeniedException("Business read permission revoked");
            }
        }
    }
    /** Must be inside the enqueue/execute transaction; row locks close the control-version TOCTOU gap. */
    public void validateFrozen(List<CacheTasks.Target> targets){
        for(var target:targets){
            var row=registry.regions().stream().filter(d->d.id().equals(target.region())).findFirst()
                    .orElseThrow(()->new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED));
            if(!fingerprint(row).equals(target.registrationHash()))
                throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            if(row.kind()==CacheDescriptor.Kind.DATA){
                if(target.resource()!=null||revisions.lockedState(row.id()).controlVersion()!=target.controlVersion())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            }else if(row.kind()==CacheDescriptor.Kind.RUNTIME){
                if(resources==null||!resources.enabled()||target.resource()==null||target.controlVersion()!=0||resources.operationVersion(row.id())!=target.resource().adapterVersion())throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            }else throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
            if(target.warmup()!=null&&(warmups==null||!target.warmup().equals(warmups.specification(row.id()))||!warmups.inspect(row.id()).available()))throw new CacheTaskRejected(CacheTaskRejected.Reason.CHANGED);
        }
    }
    public static String permission(CacheTasks.Action action){
        if(action==null)throw invalid();
        return switch(action){case CLEAR->"monitor:cache:clear";case PAUSE,RESUME->"monitor:cache:pause";case RESET_STATS->"monitor:cache:reset-stats";case PREWARM->"monitor:cache:prewarm";};
    }
    public static CacheDescriptor.Operation operation(CacheTasks.Action action){return switch(action){
        case CLEAR->CacheDescriptor.Operation.CLEAR;case PAUSE,RESUME->CacheDescriptor.Operation.PAUSE;case RESET_STATS->CacheDescriptor.Operation.RESET_STATS;case PREWARM->CacheDescriptor.Operation.PREWARM;};}
    private void require(CacheTasks.Action action){
        var permissions=CurrentUser.require().permissions();
        if(!permissions.contains("monitor:cache:view")||!permissions.contains(permission(action)))throw new AccessDeniedException("Cache task action denied");
    }
    private String fingerprint(CacheDescriptor row){
        var fields=new TreeMap<String,Object>();fields.put("id",row.id());fields.put("owner",row.owner());fields.put("nameKey",row.nameKey());
        fields.put("group",row.group());fields.put("scenario",row.scenario());fields.put("kind",row.kind());fields.put("scope",row.scope());
        fields.put("schemaVersion",row.schemaVersion());fields.put("permission",row.managePermission());
        fields.put("operations",row.operations().stream().map(Enum::name).sorted().toList());fields.put("invalidatedBy",new TreeSet<>(row.invalidatedBy()));
        fields.put("declared",row.policy());
        if(row.kind()==CacheDescriptor.Kind.DATA){
            fields.put("deployment",CacheRuntimePolicy.deployment(properties.policy(row.id(),row.policy()),properties.maxBytes()));
            fields.put("provider",properties.provider());fields.put("namespace",properties.namespace());fields.put("ttlJitter",properties.ttlJitter());
        }else fields.put("resourceVersion",resources==null?0:resources.operationVersion(row.id()));
        if(row.operations().contains(CacheDescriptor.Operation.PREWARM))fields.put("warmup",warmups==null?null:warmups.specification(row.id()));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private boolean matches(String filter,String actual){return filter==null||filter.isBlank()||filter.equals(actual);}
    private static BizException invalid(){return new BizException(CommonErrorCode.BAD_REQUEST);}
}
