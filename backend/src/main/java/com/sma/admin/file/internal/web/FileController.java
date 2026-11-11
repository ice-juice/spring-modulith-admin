package com.sma.admin.file.internal.web;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.request.*;
import com.sma.admin.file.FileView;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.file.internal.service.FileUploadRequests;

@RestController @RequestMapping("/api/file/files")
@GovernedApi(group="file.files")
public class FileController implements RequestAuthorizer {
 private final FileService service;private final FileUploadRequests uploads;private final org.springframework.context.MessageSource messages;
 public FileController(FileService service,FileUploadRequests uploads,org.springframework.context.MessageSource messages){this.service=service;this.uploads=uploads;this.messages=messages;}
 @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){CurrentUser.require();}
 @Override public java.util.Map<String,String> requestResources(String method,Object[] arguments){
  return java.util.Set.of("upload","uploadImage").contains(method)?java.util.Map.of("intent",uploads.scope((String)arguments[1])):java.util.Map.of();
 }
 @GetMapping @RequiresPermission("file:file:view")
 public ApiResponse<PageResult<FileView>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword,@RequestParam(required=false)String kind){return ApiResponse.ok(service.page(page,keyword,kind));}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @RequiresPermission("file:file:upload")
 @GovernedApi(group="file.files",ownResource=true,mode=GovernedApi.Mode.NATIVE,reason="Durable file-owned upload; common transaction accepts only the task, never the storage write")
 public ResponseEntity<ApiResponse<?>> upload(@RequestPart("file")MultipartFile file,@RequestHeader(value="Idempotency-Key",required=false)String key){return uploaded(uploads.upload(key,file,false));}
 @PostMapping(value="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PermitLoggedIn @AuditAction("file:file:upload")
 @GovernedApi(group="file.files",ownResource=true,mode=GovernedApi.Mode.NATIVE,reason="Owned raster upload uses the durable file task and current file ACL")
 public ResponseEntity<ApiResponse<?>> uploadImage(@RequestPart("file")MultipartFile file,@RequestHeader(value="Idempotency-Key",required=false)String key){return uploaded(uploads.upload(key,file,true));}
 private ResponseEntity<ApiResponse<?>> uploaded(FileUploadRequests.Result result){
  var response=ResponseEntity.status(result.file()!=null?200:result.receipt().state().equals("FAILED_FINAL")?409:202).header("X-Upload-Id",result.receipt().uploadId()).cacheControl(CacheControl.noStore());
  if(result.file()!=null)return response.body(ApiResponse.ok(result.file()));
  var reason=result.receipt().state().equals("FAILED_FINAL")?RequestRejected.Reason.FAILED:result.receipt().state().equals("UNKNOWN")?RequestRejected.Reason.UNKNOWN:RequestRejected.Reason.PROCESSING;
  if(reason.status()==202)response.header("Retry-After","2");
  return response.body(ApiResponse.fail(reason.status(),reason.key(),messages.getMessage(reason.key(),null,org.springframework.context.i18n.LocaleContextHolder.getLocale()),result.receipt()));
 }
 @GetMapping("/{id}") @PermitLoggedIn public ApiResponse<FileView> metadata(@PathVariable long id){return ApiResponse.ok(service.metadata(id));}
 @GetMapping("/{id}/content") @PermitLoggedIn
 public ResponseEntity<Resource> content(@PathVariable long id,@RequestParam(defaultValue="false")boolean inline)throws IOException{
  var result=service.content(id);boolean preview=inline&&result.previewable();
  var disposition=(preview?ContentDisposition.inline():ContentDisposition.attachment()).filename(result.name(),StandardCharsets.UTF_8).build();
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.contentType())).contentLength(result.sizeBytes())
   .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
   .cacheControl(CacheControl.noStore()).body(new FileSystemResource(result.path()));
 }
 @DeleteMapping("/{id}") @RequiresPermission("file:file:delete")
 public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return ApiResponse.ok();}
}
