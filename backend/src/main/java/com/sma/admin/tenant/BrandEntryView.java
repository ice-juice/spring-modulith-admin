package com.sma.admin.tenant;

import java.util.Map;

/** Public projection only. Never exposes source file IDs, drafts, override instructions or storage bindings. */
public record BrandEntryView(Entry entry, Branding branding, LoginCapabilities login) {
    public record Entry(Long tenantId, Long domainId, String portal) {}
    public record Branding(Long revisionId, long revision, BrandContent.Identity identity, BrandContent.Login login,
            BrandContent.Legal legal, BrandContent.Links links, BrandContent.Appearance appearance,
            Map<BrandContent.AssetSlot,String> assets,String legacyBackgroundUrl) {
        public Branding { assets=Map.copyOf(assets); }
    }
    public record LoginCapabilities(boolean passwordEnabled, boolean captchaEnabled) {}
}
