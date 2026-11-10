package com.sma.admin.monitor.internal.web;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.DatabaseDocService;
@RestController @RequestMapping("/api/monitor/database") @RequiresPermission("monitor:database:view")
public class DatabaseDocController {
 private final DatabaseDocService service;public DatabaseDocController(DatabaseDocService service){this.service=service;}
 @GetMapping public ApiResponse<com.sma.admin.monitor.DatabaseSchema> schema()throws SQLException{return ApiResponse.ok(service.schema());}
 @GetMapping("/markdown") public ResponseEntity<byte[]> markdown()throws SQLException{return ResponseEntity.ok().contentType(new MediaType("text","markdown",StandardCharsets.UTF_8))
  .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("database-schema.md").build().toString())
  .cacheControl(CacheControl.noStore()).body(service.markdown().getBytes(StandardCharsets.UTF_8));}
}
