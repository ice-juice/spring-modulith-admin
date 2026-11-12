package com.sma.admin.defense.internal.web;

import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.SecurityEvents;

@RestController @RequestMapping("/api/defense/platform-events") @RequiresPermission("defense:platform:view")
@GovernedApi(group="defense.platform-events",mode=GovernedApi.Mode.QUERY,reason="Platform evidence requires deployment operator and explicit permission; no tenant traversal or mutation")
public class PlatformSecurityEventController implements RequestAuthorizer {
    private final SecurityEvents events;
    public PlatformSecurityEventController(SecurityEvents events){this.events=events;}
    @Override public void authorizeRequest(String method,Map<String,String> variables){CurrentUser.require();}
    @GetMapping public ResponseEntity<ApiResponse<List<SecurityEventResponse>>> recent(@RequestParam(defaultValue="20") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="false") boolean includeMetadata){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(events.platform(limit).stream().map(view->SecurityEventResponse.from(view,includeMetadata)).toList()));
    }
    @GetMapping("/{eventId}") public ResponseEntity<ApiResponse<SecurityEventResponse>> detail(@PathVariable UUID eventId,@RequestParam(defaultValue="false") boolean includeMetadata){
        var event=events.platform(eventId).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(SecurityEventResponse.from(event,includeMetadata)));
    }
}
