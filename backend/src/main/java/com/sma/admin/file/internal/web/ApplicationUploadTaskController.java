package com.sma.admin.file.internal.web;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitApplicationSelf;
import com.sma.admin.file.FileUploadApi;
import org.springframework.web.bind.annotation.*;

/** Reviewed platform task endpoints. The file service enforces scene audience, owner, realm/client and current policy. */
@RestController @RequestMapping("/app/file/upload-tasks")
public class ApplicationUploadTaskController {
    private final FileUploadApi uploads;
    public ApplicationUploadTaskController(FileUploadApi uploads){this.uploads=uploads;}
    @PostMapping(consumes="application/json") @PermitApplicationSelf
    public ApiResponse<FileUploadApi.Task> initiate(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody UploadTaskRequests.Start input){return ApiResponse.ok(uploads.initiate(input.command(key)));}
    @GetMapping("/{id}") @PermitApplicationSelf
    public ApiResponse<FileUploadApi.Task> status(@PathVariable String id){return ApiResponse.ok(uploads.status(id));}
    @GetMapping("/{id}/parts") @PermitApplicationSelf
    public ApiResponse<List<FileUploadApi.Part>> parts(@PathVariable String id,@RequestParam(defaultValue="0")int after,@RequestParam(defaultValue="100")int limit){return ApiResponse.ok(uploads.parts(id,after,limit));}
    @PutMapping(value="/{id}/parts/{number}",consumes="application/octet-stream") @PermitApplicationSelf
    public ApiResponse<FileUploadApi.Task> putPart(@PathVariable String id,@PathVariable int number,
            @RequestHeader("X-Upload-Size")long size,@RequestHeader("X-Upload-SHA256")String checksum,HttpServletRequest request)throws IOException{
        return ApiResponse.ok(uploads.putPart(id,number,size,checksum,request.getInputStream()));
    }
    @PostMapping(value="/{id}/complete",consumes="application/json") @PermitApplicationSelf
    public ApiResponse<FileUploadApi.Task> complete(@PathVariable String id,@Valid @RequestBody UploadTaskRequests.Version input){return ApiResponse.ok(uploads.complete(id,input.version()));}
    @DeleteMapping(value="/{id}",consumes="application/json") @PermitApplicationSelf
    public ApiResponse<FileUploadApi.Task> abort(@PathVariable String id,@Valid @RequestBody UploadTaskRequests.Version input){return ApiResponse.ok(uploads.abort(id,input.version()));}
}
