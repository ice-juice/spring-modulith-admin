package com.sma.admin.tenant.internal.service;

import java.util.Set;
import java.util.EnumSet;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.WebAddresses;
import com.sma.admin.config.LegacyBrandSource;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.TenantErrorCode;
import static com.sma.admin.config.LegacyBrandSource.Key.*;

/** Pure conversion: never fetches a legacy URL, writes a draft or makes a publication decision. */
@Component
public class LegacyBrandConverter {
    private final BrandValidation validation;
    public LegacyBrandConverter(BrandValidation validation) { this.validation=validation; }
    public record Converted(BrandContent content,String legacyBackgroundUrl,Set<LegacyBrandSource.Key> excludedKeys) {
        public Converted { excludedKeys=Set.copyOf(excludedKeys); }
    }
    public Converted convert(LegacyBrandSource.Snapshot source,String tenantName) {
        Set<LegacyBrandSource.Key> excluded=EnumSet.noneOf(LegacyBrandSource.Key.class);
        source.values().forEach((key,value)->{if(value.secret()||!value.publicReadable()) excluded.add(key);});
        String name=value(source,NAME);if(name.isEmpty()) name=tenantName;
        var base=BrandContent.defaults(name);
        String blur=value(source,BACKGROUND_BLUR);
        if(!blur.isEmpty()&&!Set.of("true","false").contains(blur)) throw invalid(Set.of(BACKGROUND_BLUR));
        String background=value(source,BACKGROUND_URL);
        if(!background.isEmpty() && WebAddresses.localResource(background).isEmpty() && WebAddresses.httpLink(background).isEmpty()) throw invalid(Set.of(BACKGROUND_URL));
        if(background.startsWith("/") && java.util.Arrays.stream(java.net.URI.create(background).getPath().split("/"))
                .anyMatch(segment->segment.equals(".")||segment.equals(".."))) throw invalid(Set.of(BACKGROUND_URL));
        var content=new BrandContent(1,base.identity(),new BrandContent.Login("login1",base.login().title(),base.login().description(),"true".equals(blur)),
                new BrandContent.Legal(value(source,COPYRIGHT_OWNER),null,value(source,COPYRIGHT_URL),value(source,ICP_RECORD),value(source,ICP_URL),"",""),
                base.links(),base.appearance(),base.assets(),base.overrides());
        try { validation.validate(content); }
        catch(BizException rejected) { throw invalid(source.values().keySet()); }
        return new Converted(content,background,excluded);
    }
    private String value(LegacyBrandSource.Snapshot source,LegacyBrandSource.Key key) {
        var value=source.values().get(key);
        return value==null||value.secret()||!value.publicReadable()||value.text()==null?"":value.text();
    }
    private BizException invalid(Set<LegacyBrandSource.Key> keys) {
        String fields=keys.isEmpty()?"tenant.name":keys.stream().map(LegacyBrandSource.Key::parameter).sorted().collect(java.util.stream.Collectors.joining(", "));
        return new BizException(TenantErrorCode.BRAND_MIGRATION_INVALID,fields);
    }
}
