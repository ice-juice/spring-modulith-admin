package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.function.Supplier;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.persistence.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class StorageSceneTests {
    @Autowired StorageProfileService profiles;@Autowired StorageSceneService scenes;@Autowired SceneRevisionMapper revisions;
    @Autowired MockMvc mvc;@Autowired JsonMapper json;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TenantEntryQuery tenants;
    private SmaPrincipal actor(long tenant,String... permissions){return new SmaPrincipal(802,"scene-manager",tenant,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
    private <T>T as(long tenant,Supplier<T> work){return Users.as(actor(tenant,"file:storage:view","file:storage:manage","file:scene:view","file:scene:manage","file:scene:publish","file:file:upload"),work);}
    private StorageProfileService.Input profile(String code,String prefix,Set<String> applications){return new StorageProfileService.Input(code,code,StorageProvider.LOCAL,"default",null,null,null,prefix,null,null,null,null,applications,true,true);}
    private StorageProfileService.View profile(long tenant,String code){return as(tenant,()->profiles.create(profile(code,"assets",Set.of("file"))));}
    private StorageSceneService.Configuration config(Set<Long> ids,long selected,long size,Set<FileFormat> formats,Set<UploadMode> modes){return new StorageSceneService.Configuration(ids,selected,formats,modes,size,30);}
    private StorageSceneService.Input image(long profile,long size){return new StorageSceneService.Input("file.image","Image",config(Set.of(profile),profile,size,Set.of(FileFormat.PNG),Set.of(UploadMode.SERVER)));}
    private FileSceneAccess upload(String code){return new FileSceneAccess("file",code,FileSceneAccess.Action.UPLOAD,null,null,null);}
    @Test void draftsDoNotChangePublishedLimitsAndStorageRevisionsStayFixed(){
        var p=profile(1381,"local");as(1381,()->{
            var draft=scenes.create(image(p.id(),1024));assertThat(draft.enabled()).isFalse();assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER)).isInstanceOf(BizException.class);
            var published=scenes.publish(draft.id(),1);assertThat(published.version()).isEqualTo(2);var before=scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER);
            assertThat(before.configuration().maximumBytes()).isEqualTo(1024);assertThat(before.profile().configurationVersion()).isEqualTo(1);
            scenes.update(draft.id(),2,image(p.id(),2048));assertThat(scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER).configuration().maximumBytes()).isEqualTo(1024);
            profiles.update(p.id(),1,profile("local","changed",Set.of("file")));assertThat(scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER).profile().configurationVersion()).isEqualTo(1);
            var next=scenes.publish(draft.id(),3);assertThat(next.publishedVersion()).isEqualTo(4);var current=scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER);
            assertThat(current.configuration().maximumBytes()).isEqualTo(2048);assertThat(current.profile().configurationVersion()).isEqualTo(2);assertThat(revisions.selectCount(null)).isEqualTo(2);return null;
        });
    }
    @Test void tenantProfileOwnershipApplicationGrantAndHardCatalogLimitsCannotBeWidened(){
        var own=profile(1382,"same");var foreign=profile(1383,"same");as(1382,()->{
            assertThatThrownBy(()->scenes.create(image(foreign.id(),1024))).isInstanceOf(BizException.class);
            var ungranted=profiles.create(profile("other","assets",Set.of("other")));assertThatThrownBy(()->scenes.create(image(ungranted.id(),1024))).isInstanceOf(BizException.class);
            for(var input:List.of(image(own.id(),5L*1024*1024+1),new StorageSceneService.Input("file.image","Image",config(Set.of(own.id()),own.id(),1024,Set.of(FileFormat.PDF),Set.of(UploadMode.SERVER))),
                    new StorageSceneService.Input("file.image","Image",config(Set.of(own.id()),own.id(),1024,Set.of(FileFormat.PNG),Set.of(UploadMode.DIRECT))),
                    new StorageSceneService.Input("file.unregistered","Missing",image(own.id(),1024).configuration())))assertThatThrownBy(()->scenes.create(input)).isInstanceOf(BizException.class);
            var draft=scenes.create(image(own.id(),1024));scenes.publish(draft.id(),1);
            assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),foreign.id(),UploadMode.SERVER)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),own.id(),UploadMode.MULTIPART)).isInstanceOf(BizException.class);
            return null;
        });
        var other=as(1383,()->scenes.create(image(foreign.id(),1024)));
        as(1382,()->{assertThatThrownBy(()->scenes.get(other.id())).isInstanceOf(BizException.class);assertThatThrownBy(()->scenes.publish(other.id(),1)).isInstanceOf(BizException.class);return null;});
    }
    @Test void profileStopsApplicationRevocationAndSceneDisableBlockAdmissionWithoutRewritingHistory(){
        var p=profile(1384,"stop");as(1384,()->{
            var draft=scenes.create(image(p.id(),1024));scenes.publish(draft.id(),1);profiles.stop(p.id(),1,StorageProfileService.Stop.UPLOADS);
            assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER)).isInstanceOf(BizException.class);
            profiles.update(p.id(),2,profile("stop","assets",Set.of("other")));
            assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER)).isInstanceOf(BizException.class);
            profiles.update(p.id(),3,profile("stop","assets",Set.of("file")));assertThat(scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER).profile().configurationVersion()).isEqualTo(1);
            scenes.disable(draft.id(),2);assertThatThrownBy(()->scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->scenes.publish(draft.id(),2)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.STORAGE_VERSION_CONFLICT));
            assertThat(scenes.published(draft.id()).version()).isEqualTo(2);return null;
        });
    }
    @Test void preferredStorageMustBePublishedAndRuntimeStillRequiresScenePolicy(){
        var a=profile(1385,"a");var b=profile(1385,"b");as(1385,()->{
            var draft=scenes.create(new StorageSceneService.Input("file.attachment","Attachments",config(Set.of(a.id(),b.id()),a.id(),4096,Set.of(FileFormat.PDF),Set.of(UploadMode.SERVER,UploadMode.MULTIPART))));
            scenes.publish(draft.id(),1);assertThat(scenes.resolveUpload(upload("file.attachment"),null,UploadMode.SERVER).profile().id()).isEqualTo(a.id());
            assertThat(scenes.resolveUpload(upload("file.attachment"),b.id(),UploadMode.MULTIPART).profile().id()).isEqualTo(b.id());return null;
        });
        assertThatThrownBy(()->Users.as(actor(1385,"file:scene:view"),()->scenes.resolveUpload(upload("file.attachment"),null,UploadMode.SERVER))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->ActorContext.withinJob(1385,"worker",()->scenes.resolveUpload(upload("file.attachment"),null,UploadMode.SERVER))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void managementHttpSeparatesEditingPublishingAndTenantAccess()throws Exception{
        var p=profile(1386,"http");var editor=actor(1386,"file:scene:manage","file:scene:view");String key=UUID.randomUUID().toString();
        var response=mvc.perform(post("/api/file/scenes").with(Users.request(editor)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(image(p.id(),1024))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();String id=json.readTree(response).path("data").path("id").asString();
        mvc.perform(post("/api/file/scenes/"+id+"/publish").with(Users.request(editor)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/file/scenes/"+id+"/publish").with(Users.request(actor(1386,"file:scene:publish"))).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.enabled").value(true));
        mvc.perform(get("/api/file/scenes/"+id+"/published").with(Users.request(editor))).andExpect(status().isOk()).andExpect(jsonPath("$.data.profiles[0].id").value(p.id().toString()));
        mvc.perform(get("/api/file/scenes/"+id).with(Users.request(actor(1387,"file:scene:view")))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/api/file/scenes/catalog").with(Users.request(editor))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }
    @Test void disabledTenantCannotObtainAnUploadSnapshotEvenWithValidScenePermission(){
        var p=profile(1388,"tenant");as(1388,()->{var scene=scenes.create(image(p.id(),1024));scenes.publish(scene.id(),1);return null;});
        doReturn(Optional.of(new TenantEntryQuery.SecurityState(1388,2,false,false))).when(tenants).securityState(1388);
        assertThatThrownBy(()->as(1388,()->scenes.resolveUpload(upload("file.image"),null,UploadMode.SERVER))).isInstanceOf(AccessDeniedException.class);
    }
}
