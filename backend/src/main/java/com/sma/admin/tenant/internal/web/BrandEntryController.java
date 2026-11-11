package com.sma.admin.tenant.internal.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitAnonymous;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.BrandEntryView;
import com.sma.admin.tenant.internal.service.BrandEntryService;

@RestController @RequestMapping("/api/tenant")
public class BrandEntryController {
    private final BrandEntryService service;
    public BrandEntryController(BrandEntryService service) { this.service=service; }

    @GetMapping("/entry") @PermitAnonymous
    public ResponseEntity<ApiResponse<BrandEntryView>> entry() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.entry()));
    }

    @GetMapping("/brand-images/{revisionId}/{slot}") @PermitAnonymous
    public ResponseEntity<byte[]> image(@PathVariable long revisionId,@PathVariable BrandContent.AssetSlot slot) {
        var image=service.image(revisionId,slot);
        if(image.isEmpty()) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options","nosniff").body(image.get().bytes());
    }
}
