package com.sma.admin.tenant.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.admin.tenant.BrandContent;

/** Deployment-owned defaults, snapshotted on publication. Files cannot be inherited across tenant ownership. */
@ConfigurationProperties("sma.tenant.brand-defaults")
public record BrandDefaultsProperties(String revision,BrandContent content) {
    public BrandDefaultsProperties {
        revision=revision==null?"builtin-1":revision;
        content=content==null?BrandContent.defaults("Springboot Modulith Admin"):content;
        if(!revision.matches("[a-zA-Z0-9_-]{1,64}") || !content.assets().isEmpty() || !content.overrides().isEmpty()) throw new IllegalArgumentException("Invalid brand defaults");
        new BrandValidation(new LoginTemplates()).validate(content);
    }
}
