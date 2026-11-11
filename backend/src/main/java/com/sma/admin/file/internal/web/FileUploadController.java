package com.sma.admin.file.internal.web;

import java.util.Map;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileUploadReceipt;
import com.sma.admin.file.internal.service.FileUploadRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/file/uploads")
@GovernedApi(group="file.uploads",ownResource=true,mode=GovernedApi.Mode.NATIVE,reason="Owned upload receipt reconciliation; no file write or execution retry")
public class FileUploadController implements RequestAuthorizer {
    private final FileUploadRequests uploads;
    public FileUploadController(FileUploadRequests uploads){this.uploads=uploads;}
    @Override public void authorizeRequest(String method,Map<String,String> pathVariables){CurrentUser.require();}
    @Override public boolean readOnlyRequest(String method,Object[] arguments){return method.equals("lookup");}
    public record Lookup(@NotNull @Pattern(regexp="[A-Za-z0-9_.:-]{16,128}") String key){
        @Override public String toString(){return "UploadLookup[REDACTED]";}
    }
    @GetMapping("/{id}") @PermitLoggedIn
    public ResponseEntity<ApiResponse<FileUploadReceipt>> receipt(@PathVariable String id){return response(uploads.receipt(id));}
    @PostMapping("/lookup") @PermitLoggedIn
    public ResponseEntity<ApiResponse<FileUploadReceipt>> lookup(@Valid @RequestBody Lookup lookup){return response(uploads.byKey(lookup.key()));}
    private ResponseEntity<ApiResponse<FileUploadReceipt>> response(FileUploadReceipt receipt){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(receipt));}
}
