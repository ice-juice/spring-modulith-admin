package com.sma.admin.tenant.internal.service;

import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.BizException;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.TenantErrorCode;

/** Authenticated, bounded, transient projection. Does not save a draft, reference or publication. */
@Service
public class BrandPreviewService {
    private final BrandDraftService drafts;
    private final BrandValidation validation;
    private final BrandResolver resolver;
    private final BrandDefaultsProperties defaults;
    private final BrandAssets assets;
    public BrandPreviewService(BrandDraftService drafts,BrandValidation validation,BrandResolver resolver,
            BrandDefaultsProperties defaults,BrandAssets assets) {
        this.drafts=drafts;this.validation=validation;this.resolver=resolver;this.defaults=defaults;this.assets=assets;
    }
    public record Preview(BrandContent content,String defaultsRevision,String legacyBackgroundUrl,Map<BrandContent.AssetSlot,String> images) {
        public Preview { images=Map.copyOf(images); }
        @Override public String toString() { return "BrandPreview[defaultsRevision="+defaultsRevision+", imageCount="+images.size()+"]"; }
    }
    // Image authorization takes file row locks; this transaction is deliberately not JDBC read-only.
    @Transactional
    public Preview preview(long expectedVersion,BrandContent input) {
        var draft=drafts.get(); // Enforces brand:view and trusted tenant/portal even without images.
        if(expectedVersion!=draft.version()) throw new BizException(TenantErrorCode.STALE);
        var effective=resolver.resolve(validation.validate(input),defaults.content());
        var prepared=assets.prepare(effective);
        Map<BrandContent.AssetSlot,String> images=new EnumMap<>(BrandContent.AssetSlot.class);
        effective.assets().forEach((slot,id)->images.put(slot,"data:image/png;base64,"+Base64.getEncoder().encodeToString(prepared.get(id).bytes())));
        boolean legacyAllowed=!input.assets().containsKey(BrandContent.AssetSlot.LOGIN_BACKGROUND)
                && input.overrides().getOrDefault(BrandContent.Field.LOGIN_BACKGROUND,BrandContent.OverrideMode.OVERRIDE)==BrandContent.OverrideMode.OVERRIDE;
        return new Preview(effective,defaults.revision(),legacyAllowed?draft.legacyBackgroundUrl():null,images);
    }
}
