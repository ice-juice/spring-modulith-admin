package com.sma.admin.file.internal.web;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileView;
import com.sma.admin.file.internal.service.FileService;

@RestController @RequestMapping("/api/file/files")
public class FileController {
 private final FileService service;public FileController(FileService service){this.service=service;}
 @GetMapping @RequiresPermission("file:file:view")
 public ApiResponse<PageResult<FileView>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword,@RequestParam(required=false)String kind){return ApiResponse.ok(service.page(page,keyword,kind));}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @RequiresPermission("file:file:upload")
 public ApiResponse<FileView> upload(@RequestPart("file")MultipartFile file){return ApiResponse.ok(service.upload(file));}
 @PostMapping(value="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PermitLoggedIn @AuditAction("file:file:upload")
 public ApiResponse<FileView> uploadImage(@RequestPart("file")MultipartFile file){return ApiResponse.ok(service.uploadImage(file));}
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
