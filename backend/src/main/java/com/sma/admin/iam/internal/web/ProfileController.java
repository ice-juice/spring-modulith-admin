package com.sma.admin.iam.internal.web;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.ProfileService;

@RestController @RequestMapping("/api/iam/profile") @PermitLoggedIn
public class ProfileController {
 private final ProfileService service;
 public ProfileController(ProfileService service){this.service=service;}
 @GetMapping public ApiResponse<ProfileService.View> get(){return ApiResponse.ok(service.get());}
 @PutMapping @AuditAction("iam:profile:update")
 public ApiResponse<ProfileService.View> update(@Valid @RequestBody ProfileService.Input input){return ApiResponse.ok(service.update(input));}
}
