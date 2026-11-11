package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.PreferenceService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.*;

@IntegrationTest
class BrandAppearanceTests {
    @Autowired PreferenceService preferences;
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    private static final Set<String> PERMISSIONS=Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback");
    private static SmaPrincipal actor(long tenant,String portal) { return new SmaPrincipal(899,"appearance",tenant,portal,null,false,DataAccess.own(),PERMISSIONS,"UTC"); }
    private static BrandContent appearance(String skin,boolean personal) {
        var base=BrandContent.defaults("Appearance tenant");
        return new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),new BrandContent.Appearance("",skin,personal),Map.of(),Map.of());
    }
    @Test void publishedPolicyAppliesAfterRawCacheWithoutDestroyingPersonalChoice() {
        Users.as(actor(7490,"admin"),()->{
            preferences.save(new PreferenceService.Input("zh-CN","UTC","light",true,false,"celadon"));
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("celadon");
            drafts.save(0,appearance("amber",false));
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("celadon"); // Draft is not policy.
            var first=publications.publish(UUID.randomUUID().toString(),1,null);
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("amber");
            preferences.save(new PreferenceService.Input("en-US","UTC","dark",true,false,"iris"));
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("amber");
            drafts.save(1,appearance("amber",true));var second=publications.publish(UUID.randomUUID().toString(),2,first.revisionId());
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("celadon");
            assertThat(preferences.get(900,null,null).skin()).isEqualTo("amber"); // No personal preference uses tenant default.
            publications.rollback(UUID.randomUUID().toString(),first.revisionId(),second.revisionId());
            assertThat(preferences.get(899,null,null).skin()).isEqualTo("amber");
            return null;
        });
        Users.as(actor(7491,"admin"),()->{assertThat(preferences.get(900,null,null).skin()).isEqualTo("iris");return null;});
        Users.as(actor(7490,"partner"),()->{assertThat(preferences.get(900,null,null).skin()).isEqualTo("iris");return null;});
    }
}
