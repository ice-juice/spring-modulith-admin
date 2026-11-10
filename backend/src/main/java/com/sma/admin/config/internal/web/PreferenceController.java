package com.sma.admin.config.internal.web;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.*;
import com.sma.admin.config.PreferenceView;
import com.sma.admin.config.internal.service.PreferenceService;

@RestController @RequestMapping("/api/config/preferences") @PermitLoggedIn
public class PreferenceController {
 private final PreferenceService service;
 public PreferenceController(PreferenceService service){this.service=service;}
 @GetMapping public ApiResponse<PreferenceView> get(){return ApiResponse.ok(service.get(CurrentUser.require().userId(),null,null));}
 @PutMapping @AuditAction(value="config:preference:update",path="/preferences") public ApiResponse<PreferenceView> save(@Valid @RequestBody PreferenceService.Input input){return ApiResponse.ok(service.save(input));}
 @DeleteMapping @AuditAction(value="config:preference:reset",path="/preferences") public ApiResponse<Void> reset(){service.reset();return ApiResponse.ok();}
}
