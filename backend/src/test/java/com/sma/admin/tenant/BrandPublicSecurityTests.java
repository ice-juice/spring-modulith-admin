package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import com.sma.admin.tenant.internal.service.*;

/** P11 X4: real HTTP filter chain, database publication/reference state, and local image bytes. */
@IntegrationTest @AutoConfigureMockMvc
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.address=127.0.0.1")
class BrandPublicSecurityTests {
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired FileService files;
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    @Autowired TenantRecordMapper tenants;
    private static final Set<String> EDIT=Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback");
    private static SmaPrincipal actor(long user,long tenant,String portal,boolean admin,Set<String> permissions) {
        return new SmaPrincipal(user,"private-uploader-"+user,tenant,portal,null,admin,DataAccess.own(),permissions,"UTC");
    }
    private static SmaPrincipal owner(long tenant) { return actor(tenant,tenant,"admin",false,EDIT); }
    private static String host(long tenant,String portal) { return "t"+tenant+"-"+portal+".test"; }
    private record Fixture(long tenant,long file,long revision,String url) {}
    private Fixture publish(long tenant) {
        return Users.as(owner(tenant),()->{
            long id=files.uploadImage(new MockMultipartFile("file","private-original-name.png","image/png",png())).id();
            drafts.save(0,brandContent("Published "+tenant,id));
            var receipt=publications.publish(UUID.randomUUID().toString(),1,null);
            return new Fixture(tenant,id,receipt.revisionId(),"/api/tenant/brand-images/"+receipt.revisionId()+"/LOGO_LIGHT");
        });
    }
    private static BrandContent brandContent(String name,long file) {
        var base=BrandContent.defaults(name);
        return new BrandContent(1,base.identity(),base.login(),base.legal(),new BrandContent.Links("https://example.com/help","","","",""),
                base.appearance(),Map.of(BrandContent.AssetSlot.LOGO_LIGHT,file),Map.of());
    }
    private static byte[] png() {
        try {
            var image=new BufferedImage(12,8,BufferedImage.TYPE_INT_ARGB);image.setRGB(2,2,0xff123456);
            var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);out.write("private-trailing-payload".getBytes(java.nio.charset.StandardCharsets.US_ASCII));return out.toByteArray();
        } catch(java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static void keys(Map<?,?> object,String... names) {
        assertThat(object.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder(names);
    }
    @Test void publicProjectionIsAnExactAllowlistAndQueryOrForwardedHeadersCannotSwitchTenant() throws Exception {
        var fixture=publish(78101);Users.as(owner(78102),()->null);
        Users.as(owner(78101),()->drafts.save(1,BrandContent.defaults("unpublished-next-draft")));
        var response=mvc.perform(get("/api/tenant/entry").header("Host",host(78101,"admin"))
                .header("X-Tenant-Id","78102").header("X-Forwarded-Host",host(78102,"admin"))
                .header("Forwarded","host="+host(78102,"admin"))
                .param("tenantId","78102").param("portal","partner").param("revisionId","0")
                .with(request->{request.setRemoteAddr("203.0.113.88");return request;}))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.entry.tenantId").value("78101"))
                .andExpect(jsonPath("$.data.entry.portal").value("admin"))
                .andExpect(jsonPath("$.data.branding.assets.LOGO_LIGHT").value(fixture.url())).andReturn().getResponse();
        Map<?,?> root=json.readValue(response.getContentAsByteArray(),Map.class),data=(Map<?,?>)root.get("data");
        keys(data,"entry","branding","login");keys((Map<?,?>)data.get("entry"),"tenantId","domainId","portal");
        Map<?,?> brand=(Map<?,?>)data.get("branding");keys(brand,"revisionId","revision","identity","login","legal","links","appearance","assets","legacyBackgroundUrl");
        keys((Map<?,?>)brand.get("identity"),"systemName","shortName","englishName");
        keys((Map<?,?>)brand.get("login"),"templateId","title","description","backgroundBlur");
        keys((Map<?,?>)brand.get("legal"),"copyrightOwner","copyrightStartYear","copyrightUrl","icpRecord","icpUrl","policeRecord","policeUrl");
        keys((Map<?,?>)brand.get("links"),"website","help","support","privacy","agreement");
        keys((Map<?,?>)brand.get("appearance"),"primaryColor","defaultSkin","allowPersonalSkin");
        keys((Map<?,?>)data.get("login"),"passwordEnabled","captchaEnabled");
        assertThat(response.getContentAsString()).doesNotContain("unpublished-next-draft","private-original-name","private-uploader","sourceFileId","storageKey","operationId","publisherId","overrides","securityGeneration");
    }
    @Test void onlyGetAndHeadOfPublishedImageArePublicAndOriginalOrManagementRemainProtected() throws Exception {
        var fixture=publish(78103);String hostname=host(fixture.tenant(),"admin");
        var response=mvc.perform(get(fixture.url()).header("Host",hostname).header("Authorization","Bearer expired"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private-trailing-payload");
        mvc.perform(head(fixture.url()).header("Host",hostname).header("Authorization","Bearer expired"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control","no-store"));
        // MockMvc dispatches directly without the servlet container's HEAD suppression. Verify wire bytes too.
        try(var socket=new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
            String request="HEAD "+fixture.url()+" HTTP/1.1\r\nHost: "+hostname+"\r\nAuthorization: Bearer expired\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            byte[] wire=socket.getInputStream().readNBytes(8193);assertThat(wire.length).isLessThanOrEqualTo(8192);
            String text=new String(wire,java.nio.charset.StandardCharsets.ISO_8859_1);int headers=text.indexOf("\r\n\r\n");
            assertThat(text).startsWith("HTTP/1.1 200");assertThat(headers).isGreaterThan(0);
            assertThat(text.substring(headers+4)).isEmpty();
            assertThat(text.substring(0,headers).toLowerCase(Locale.ROOT)).contains("content-type: image/png","cache-control: no-store","x-content-type-options: nosniff",
                    "content-length: "+response.getContentAsByteArray().length);
        }
        for(String path:List.of("/api/file/files/"+fixture.file()+"/content","/api/tenant/brands/draft","/api/tenant/brands/revisions/"+fixture.revision(),
                "/api/tenant/brands/operations/not-a-public-operation",fixture.url()+"/extra","/api/tenant/entry/extra"))
            mvc.perform(get(path).header("Host",hostname)).andExpect(status().isUnauthorized());
        for(var method:List.of(org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.PUT,org.springframework.http.HttpMethod.DELETE))
            mvc.perform(request(method,fixture.url()).header("Host",hostname).header("Authorization","Bearer expired")).andExpect(status().isUnauthorized());
    }
    @Test void crossTenantPortalMissingSlotAndSuspendedOrUnknownHostCannotServeSnapshot() throws Exception {
        var fixture=publish(78104);Users.as(owner(78105),()->null);Users.as(actor(78104,78104,"partner",false,EDIT),()->null);
        for(String hostname:List.of(host(78105,"admin"),host(78104,"partner"),"unknown-x4.invalid"))
            mvc.perform(get(fixture.url()).header("Host",hostname).param("tenantId","78104").header("X-Tenant-Id","78104")
                    .header("X-Forwarded-Host",host(78104,"admin")).with(request->{request.setRemoteAddr("203.0.113.88");return request;}))
                    .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/tenant/brand-images/"+fixture.revision()+"/LOGO_DARK").header("Host",host(78104,"admin"))).andExpect(status().isNotFound());
        Users.as(owner(78104),()->{var tenant=tenants.selectById(78104L);tenant.setStatus("SUSPENDED");tenant.setSecurityGeneration(2L);tenants.updateById(tenant);return null;});
        mvc.perform(get(fixture.url()).header("Host",host(78104,"admin"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/tenant/entry").header("Host",host(78104,"admin"))).andExpect(status().isNotFound());
    }
    @Test void brandReadGrantIsLimitedToReferencedFilesAndCannotCrossTenantEvenForSuperAdmin() {
        var fixture=publish(78106);
        long unrelated=Users.as(owner(78106),()->files.uploadImage(new MockMultipartFile("file","unrelated.png","image/png",png())).id());
        Users.as(actor(99001,78106,"admin",false,Set.of("tenant:brand:view")),()->{
            assertThat(files.metadata(fixture.file()).id()).isEqualTo(fixture.file());
            assertThatThrownBy(()->files.metadata(unrelated)).isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(actor(99002,78106,"admin",false,Set.of()),()->{assertThatThrownBy(()->files.metadata(fixture.file())).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(actor(78106,78107,"admin",true,EDIT),()->{assertThatThrownBy(()->files.metadata(fixture.file())).isInstanceOf(BizException.class);return null;});
        Users.as(actor(78106,78106,"partner",true,EDIT),()->{assertThatThrownBy(()->files.metadata(fixture.file())).isInstanceOf(BizException.class);return null;});
    }
    @Test void documentOrSvgIdsCannotBecomeBrandAssetsAndFailedDraftDoesNotPublish() {
        Users.as(owner(78108),()->{
            for(String source:List.of("<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>","<script>malicious()</script>","%PDF-1.7\n")) {
                long id=files.upload(new MockMultipartFile("file","disguised.png","application/octet-stream",source.getBytes(java.nio.charset.StandardCharsets.US_ASCII))).id();
                assertThatThrownBy(()->drafts.save(0,brandContent("Rejected asset",id))).isInstanceOf(BizException.class);
                assertThat(drafts.get().publishedRevisionId()).isNull();assertThat(drafts.get().version()).isZero();
            }
            return null;
        });
    }
}
