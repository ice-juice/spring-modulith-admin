package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import com.sma.admin.tenant.internal.service.*;

@IntegrationTest @AutoConfigureMockMvc
class BrandEntryHttpTests {
    @Autowired MockMvcTester mvc;
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    @Autowired FileService files;
    @Autowired ParameterService parameters;
    @Autowired com.sma.admin.config.internal.service.DefinedParameterService parameterLayers;
    @Autowired TenantRecordMapper tenants;
    private static SmaPrincipal actor(long tenant,String portal) {
        return new SmaPrincipal(901,"brand-entry-editor",tenant,portal,null,false,DataAccess.own(),
                Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","config:parameter:update"),"UTC");
    }
    private static String host(long tenant,String portal) { return "t"+tenant+"-"+portal+".test"; }

    @Test void anonymousProjectionCannotPublishDraftsOrEnumeratePublicAndSecretParameters() throws Exception {
        Users.as(actor(7510,"partner"),()->{
            drafts.save(0,BrandContent.defaults("Never public draft"));
            parameters.saveGroup(null,new ParameterService.GroupInput("entry-test","Entry tests",null,0,Map.of()));
            parameters.save(null,new ParameterService.Input("custom.publicValue","entry-test","Public",null,"STRING","not-in-brand-projection",true,false,0,Map.of()));
            parameters.save(null,new ParameterService.Input("custom.secretValue","entry-test","Secret",null,"STRING","never-reveal-secret",false,true,0,Map.of()));
            parameterLayers.override("login.captchaEnabled",parameterLayers.get("login.captchaEnabled").snapshot().revision(),"false");
            return null;
        });
        var response=mvc.get().uri("/api/tenant/entry").header("Host",host(7510,"partner")).header("Authorization","Bearer expired").exchange();
        assertThat(response).hasStatusOk().hasHeader("Cache-Control","no-store").bodyJson()
                .hasPathSatisfying("$.data.entry.tenantId",v->v.assertThat().isEqualTo("7510"))
                .hasPathSatisfying("$.data.entry.portal",v->v.assertThat().isEqualTo("partner"))
                .hasPathSatisfying("$.data.branding.identity.systemName",v->v.assertThat().isEqualTo("Synthetic test tenant 7510"))
                .hasPathSatisfying("$.data.login.captchaEnabled",v->v.assertThat().isEqualTo(false));
        assertThat(response.getResponse().getContentAsString()).doesNotContain("Never public draft","not-in-brand-projection","never-reveal-secret","securityGeneration","overrides");
        assertThat(mvc.get().uri("/api/tenant/brands/draft").header("Host",host(7510,"partner"))).hasStatus(401);
    }

    @Test void onlyPublishedSnapshotImagesAreAnonymousAndRemainTenantAndPortalScoped() throws Exception {
        byte[] png=png();
        long file=Users.as(actor(7511,"admin"),()->{
            var uploaded=files.uploadImage(new MockMultipartFile("file","brand.png","image/png",png));
            var base=BrandContent.defaults("Published tenant");
            drafts.save(0,new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),base.appearance(),Map.of(BrandContent.AssetSlot.LOGO_LIGHT,uploaded.id()),Map.of()));
            return uploaded.id();
        });
        assertThat(mvc.get().uri("/api/tenant/brand-images/"+file+"/LOGO_LIGHT").header("Host",host(7511,"admin"))).hasStatus(404);
        var revision=Users.as(actor(7511,"admin"),()->publications.publish(UUID.randomUUID().toString(),1,null));
        String imageUrl="/api/tenant/brand-images/"+revision.revisionId()+"/LOGO_LIGHT";
        var response=mvc.get().uri(imageUrl).header("Host",host(7511,"admin")).header("Authorization","Bearer expired").exchange();
        assertThat(response).hasStatusOk().hasHeader("Content-Type","image/png").hasHeader("Cache-Control","no-store").hasHeader("X-Content-Type-Options","nosniff");
        var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.getResponse().getContentAsByteArray()));
        assertThat(decoded.getWidth()).isEqualTo(12);assertThat(decoded.getRGB(2,2)).isEqualTo(0xff123456);
        assertThat(mvc.get().uri("/api/tenant/entry").header("Host",host(7511,"admin"))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.branding.assets.LOGO_LIGHT",v->v.assertThat().isEqualTo(imageUrl));
        Users.as(actor(7512,"admin"),()->null);Users.as(actor(7511,"partner"),()->null);
        assertThat(mvc.get().uri(imageUrl).header("Host",host(7512,"admin"))).hasStatus(404);
        assertThat(mvc.get().uri(imageUrl).header("Host",host(7511,"partner"))).hasStatus(404);
        assertThat(mvc.get().uri("/api/file/files/"+file+"/content").header("Host",host(7511,"admin"))).hasStatus(401);
        Users.as(actor(7511,"admin"),()->drafts.save(1,BrandContent.defaults("New private draft")));
        assertThat(mvc.get().uri("/api/tenant/entry").header("Host",host(7511,"admin"))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.branding.identity.systemName",v->v.assertThat().isEqualTo("Published tenant"));
    }

    @Test void unknownAndSuspendedEntriesDoNotFallBackOrServeImages() {
        assertThat(mvc.get().uri("/api/tenant/entry").header("Host","unknown-entry.example.com")).hasStatus(404).hasHeader("Cache-Control","no-store");
        Users.as(actor(7513,"admin"),()->{
            var tenant=tenants.selectById(7513L);tenant.setStatus("SUSPENDED");tenant.setSecurityGeneration(2L);tenants.updateById(tenant);return null;
        });
        assertThat(mvc.get().uri("/api/tenant/entry").header("Host",host(7513,"admin"))).hasStatus(404);
        assertThat(mvc.get().uri("/api/tenant/brand-images/123/LOGO_LIGHT").header("Host",host(7513,"admin"))).hasStatus(404);
    }

    private static byte[] png() throws java.io.IOException {
        var image=new java.awt.image.BufferedImage(12,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(2,2,0xff123456);
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);return output.toByteArray();
    }
}
