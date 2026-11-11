package com.sma.admin.monitor.internal.service;

import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;

/** Metadata-only governance directory. Every operation is reauthorized by the operation endpoint. */
@Service
public class CacheDirectoryService {
    private final CacheRegions regions;
    private final CacheInspection inspection;
    private final MessageSource messages;
    private final CachePrewarming prewarming;
    private final CacheResources resources;
    public CacheDirectoryService(CacheRegions regions,CacheInspection inspection,MessageSource messages) {
        this(regions,inspection,messages,null);
    }
    public CacheDirectoryService(CacheRegions regions,CacheInspection inspection,MessageSource messages,CachePrewarming prewarming) {
        this(regions,inspection,messages,prewarming,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheDirectoryService(CacheRegions regions,CacheInspection inspection,MessageSource messages,CachePrewarming prewarming,CacheResources resources) {
        this.regions=regions;this.inspection=inspection;this.messages=messages;
        this.prewarming=prewarming;
        this.resources=resources;
    }
    public record Filter(String owner,String group,String scenario,String kind,String provider,String status,String keyword) {}
    public record View(String id,String name,String owner,String group,String scenario,String kind,String scope,
            int schemaVersion,String managePermission,Set<String> declaredOperations,Set<String> allowedOperations,
            Set<String> invalidatedBy,String state,CacheInspection.Observation observation,CachePrewarming.Eligibility prewarming,
            CacheResources.Observation resource,String lifecycleDescription) {
        public View {declaredOperations=Set.copyOf(declaredOperations);allowedOperations=Set.copyOf(allowedOperations);invalidatedBy=Set.copyOf(invalidatedBy);}
    }
    public List<View> list(Filter filter) {
        requireView();validate(filter);
        return regions.regions().stream().sorted(Comparator.comparing(CacheDescriptor::id))
                .filter(row->matches(filter.owner(),row.owner())&&matches(filter.group(),row.group())
                        &&matches(filter.scenario(),row.scenario())&&matches(filter.kind(),row.kind().name()))
                .map(this::view).filter(row->matches(filter.provider(),row.observation()!=null?row.observation().provider():row.resource()!=null?row.resource().provider():"adapter")
                        &&matches(filter.status(),row.state())&&keyword(filter.keyword(),row)).toList();
    }
    public View detail(String id) {
        requireView();return view(regions.regions().stream().filter(row->row.id().equals(id)).findFirst()
                .orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND)));
    }
    private View view(CacheDescriptor row) {
        var observation=row.kind()==CacheDescriptor.Kind.DATA?inspection.inspect(row.id()):null;
        var resource=row.kind()!=CacheDescriptor.Kind.DATA&&resources!=null?resources.inspect(row.id()):null;
        String state=resource!=null?resource.state():row.kind()==CacheDescriptor.Kind.PROTECTED?"PROTECTED":observation==null?"ADAPTER_REQUIRED":
                observation.control()!=null&&!observation.control().confirmed()?"UNAVAILABLE":
                observation.snapshot().size()<0?"UNAVAILABLE":
                observation.control()!=null&&Boolean.TRUE.equals(observation.control().paused())?"PAUSED":
                observation.provider().equals("none")?"DISABLED":"ACTIVE";
        var allowed=new HashSet<String>();var permissions=CurrentUser.require().permissions();
        var warmup=row.operations().contains(CacheDescriptor.Operation.PREWARM)&&prewarming!=null?prewarming.inspect(row.id()):null;
        if(row.kind()==CacheDescriptor.Kind.DATA&&permissions.contains(row.managePermission())){
            Map<CacheDescriptor.Operation,String> requirements=Map.of(CacheDescriptor.Operation.CLEAR,"monitor:cache:clear",
                    CacheDescriptor.Operation.PAUSE,"monitor:cache:pause",CacheDescriptor.Operation.EDIT_POLICY,"monitor:cache:policy",
                    CacheDescriptor.Operation.RESET_STATS,"monitor:cache:reset-stats");
            requirements.forEach((operation,permission)->{if(row.operations().contains(operation)&&permissions.contains(permission))allowed.add(operation.name());});
            if(warmup!=null&&warmup.available()&&state.equals("ACTIVE"))allowed.add("PREWARM");
        }
        if(row.kind()==CacheDescriptor.Kind.RUNTIME&&resources!=null&&state.equals("ACTIVE")){
            for(var operation:row.operations())if(resources.capability(row.id(),operation).available())allowed.add(operation.name());
        }
        return new View(row.id(),messages.getMessage(row.nameKey(),null,row.id(),LocaleContextHolder.getLocale()),row.owner(),row.group(),
                row.scenario(),row.kind().name(),row.scope().name(),row.schemaVersion(),row.managePermission(),
                row.operations().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),allowed,row.invalidatedBy(),state,observation,warmup,resource,
                resource==null?null:messages.getMessage(resource.lifecycleKey(),null,resource.lifecycleKey(),LocaleContextHolder.getLocale()));
    }
    private void requireView(){if(!CurrentUser.require().permissions().contains("monitor:cache:view"))throw new AccessDeniedException("Cache directory denied");}
    private boolean matches(String filter,String value){return filter==null||filter.isBlank()||filter.equals(value);}
    private boolean keyword(String keyword,View row){return keyword==null||keyword.isBlank()||(row.id()+" "+row.name()).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));}
    private void validate(Filter filter){
        if(filter==null)throw new BizException(CommonErrorCode.BAD_REQUEST);
        for(var value:Arrays.asList(filter.owner(),filter.group(),filter.scenario(),filter.kind(),filter.provider(),filter.status(),filter.keyword()))
            if(value!=null&&value.length()>160)throw new BizException(CommonErrorCode.BAD_REQUEST);
    }
}
