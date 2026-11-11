package com.sma.admin.file.internal.web;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileUploadApi;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/file/upload-tasks")
@GovernedApi(group="file.upload-tasks",ownResource=true,mode=GovernedApi.Mode.NATIVE,
        reason="File-owned immutable task/part claims and receipts; byte I/O must not join a common transaction")
public class UploadTaskController implements RequestAuthorizer {
    private final FileUploadApi uploads;
    public UploadTaskController(FileUploadApi uploads){this.uploads=uploads;}
    @Override public void authorizeRequest(String method,Map<String,String> variables){CurrentUser.require();}
    @PostMapping(consumes="application/json") @PermitLoggedIn
    public ApiResponse<FileUploadApi.Task> initiate(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody UploadTaskRequests.Start input){return ApiResponse.ok(uploads.initiate(input.command(key)));}
    @GetMapping("/{id}") @PermitLoggedIn
    public ApiResponse<FileUploadApi.Task> status(@PathVariable String id){return ApiResponse.ok(uploads.status(id));}
    @GetMapping("/{id}/parts") @PermitLoggedIn
    public ApiResponse<List<FileUploadApi.Part>> parts(@PathVariable String id,@RequestParam(defaultValue="0")int after,@RequestParam(defaultValue="100")int limit){return ApiResponse.ok(uploads.parts(id,after,limit));}
    @PutMapping(value="/{id}/parts/{number}",consumes="application/octet-stream") @PermitLoggedIn
    public ApiResponse<FileUploadApi.Task> putPart(@PathVariable String id,@PathVariable int number,
            @RequestHeader("X-Upload-Size")long size,@RequestHeader("X-Upload-SHA256")String checksum,HttpServletRequest request)throws IOException{
        return ApiResponse.ok(uploads.putPart(id,number,size,checksum,request.getInputStream()));
    }
    @PostMapping(value="/{id}/complete",consumes="application/json") @PermitLoggedIn
    public ApiResponse<FileUploadApi.Task> complete(@PathVariable String id,@Valid @RequestBody UploadTaskRequests.Version input){return ApiResponse.ok(uploads.complete(id,input.version()));}
    @DeleteMapping(value="/{id}",consumes="application/json") @PermitLoggedIn
    public ApiResponse<FileUploadApi.Task> abort(@PathVariable String id,@Valid @RequestBody UploadTaskRequests.Version input){return ApiResponse.ok(uploads.abort(id,input.version()));}
}
