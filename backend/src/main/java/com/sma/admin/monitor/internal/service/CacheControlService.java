package com.sma.admin.monitor.internal.service;
import java.time.Duration;
import java.util.List;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.*;

@Service
public class CacheControlService {
    private final CacheGovernance governance;
    public CacheControlService(CacheGovernance governance){this.governance=governance;}
    public record Version(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=256) String reason) {}
    public record PolicyInput(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=256) String reason,
            @Min(1) @Max(86400000) long ttlMillis,@Min(0) @Max(86400000) long negativeTtlMillis,
            @Min(1) long maxEntries,@Min(1) long maxBytes,@Min(1) int maxEntryBytes) {}
    public CacheGovernance.State state(String id){return governance.state(id);}
    public CacheGovernance.State policy(String id,PolicyInput input){
        final CachePolicy policy;
        try{policy=new CachePolicy(Duration.ofMillis(input.ttlMillis()),Duration.ofMillis(input.negativeTtlMillis()),input.maxEntries(),input.maxBytes(),input.maxEntryBytes());}
        catch(IllegalArgumentException invalid){throw new BizException(CommonErrorCode.BAD_REQUEST);}
        return governance.change(id,new CacheGovernance.Command(CacheGovernance.Action.SET_POLICY,input.expectedVersion(),policy,input.reason()));
    }
    public CacheGovernance.State command(String id,CacheGovernance.Action action,Version input){return governance.change(id,new CacheGovernance.Command(action,input.expectedVersion(),null,input.reason()));}
    public List<CacheGovernance.History> history(String id,int limit){return governance.history(id,limit);}
}
