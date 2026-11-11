package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.FileService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.*;
import com.sma.admin.tenant.internal.persistence.*;

@IntegrationTest
class BrandPreviewTests {
    @Autowired BrandPreviewService previews;
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    @Autowired BrandHeadMapper heads;
    @Autowired BrandReferenceMapper references;
    @Autowired BrandRevisionMapper revisions;
    @Autowired FileService files;
    private static SmaPrincipal actor(long user,long tenant,Set<String> permissions) {
        return new SmaPrincipal(user,"preview-"+user,tenant,"admin",null,false,DataAccess.own(),permissions,"UTC");
    }
    private static final Set<String> EDIT=Set.of("tenant:brand:view","tenant:brand:edit");
    private static BrandContent content(long file) {
        var base=BrandContent.defaults("Unsaved preview");
        return new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),base.appearance(),
                Map.of(BrandContent.AssetSlot.LOGO_LIGHT,file),Map.of(BrandContent.Field.WELCOME_TITLE,BrandContent.OverrideMode.INHERIT));
    }
    private static byte[] png() {
        try { var image=new java.awt.image.BufferedImage(12,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray(); }
        catch(java.io.IOException failure) { throw new AssertionError(failure); }
    }
    @Test void unsavedPreviewUsesSafePngWithoutCreatingHeadsReferencesOrRevisions() {
        Users.as(actor(890,7480,EDIT),()->{
            var file=files.uploadImage(new MockMultipartFile("file","preview.png","image/png",png()));
            var preview=previews.preview(0,content(file.id()));
            assertThat(preview.content().identity().systemName()).isEqualTo("Unsaved preview");
            assertThat(preview.content().overrides()).isEmpty();
            var value=preview.images().get(BrandContent.AssetSlot.LOGO_LIGHT);
            assertThat(value).startsWith("data:image/png;base64,");
            assertThat(Base64.getDecoder().decode(value.substring(value.indexOf(',')+1))).startsWith((byte)137,(byte)80,(byte)78,(byte)71);
            assertThat(preview.toString()).doesNotContain(value,"Unsaved preview");
            assertThat(heads.selectCount(null)).isZero();assertThat(references.selectCount(null)).isZero();assertThat(revisions.selectCount(null)).isZero();
            assertThat(drafts.get().version()).isZero();assertThat(drafts.get().publishedRevisionId()).isNull();
            files.delete(file.id()); // A preview did not pin an unsaved upload forever.
            return null;
        });
    }
    @Test void previewRejectsWrongVersionPrivateFilesForeignTenantsAndMissingPermission() {
        var owner=actor(891,7481,EDIT);
        long file=Users.as(owner,()->files.uploadImage(new MockMultipartFile("file","private.png","image/png",png())).id());
        Users.as(actor(892,7481,EDIT),()->{assertThatThrownBy(()->previews.preview(0,content(file))).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(actor(891,7482,EDIT),()->{assertThatThrownBy(()->previews.preview(0,content(file))).isInstanceOf(BizException.class);return null;});
        Users.as(actor(891,7481,Set.of()),()->{assertThatThrownBy(()->previews.preview(0,content(file))).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(owner,()->{drafts.save(0,content(file));
            assertThatThrownBy(()->previews.preview(0,content(file))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(TenantErrorCode.STALE));
            var hidden=content(file);hidden=new BrandContent(1,hidden.identity(),hidden.login(),hidden.legal(),hidden.links(),hidden.appearance(),hidden.assets(),Map.of(BrandContent.Field.LOGO_LIGHT,BrandContent.OverrideMode.HIDE));
            assertThat(previews.preview(1,hidden).images()).isEmpty();assertThat(drafts.get().version()).isEqualTo(1);return null;});
    }
    @Test void resultLookupIsReadOnlyOwnedAndRequiresTheOriginalActionPermission() {
        var writer=actor(893,7483,Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback"));
        String operation=UUID.randomUUID().toString();
        var receipt=Users.as(writer,()->{drafts.save(0,BrandContent.defaults("Receipt"));
            assertThat(publications.operationResult(operation).status()).isEqualTo("NOT_OBSERVED");
            return publications.publish(operation,1,null);});
        Users.as(writer,()->{assertThat(publications.operationResult(operation).receipt()).isEqualTo(receipt);
            assertThat(revisions.selectCount(null)).isEqualTo(1);return null;});
        Users.as(actor(894,7483,writer.permissions()),()->{assertThat(publications.operationResult(operation).status()).isEqualTo("NOT_OBSERVED");return null;});
        Users.as(actor(893,7484,writer.permissions()),()->{assertThat(publications.operationResult(operation).receipt()).isNull();return null;});
        Users.as(actor(893,7483,Set.of("tenant:brand:view")),()->{assertThatThrownBy(()->publications.operationResult(operation)).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(writer,()->{
            String rollback=UUID.randomUUID().toString();var rolled=publications.rollback(rollback,receipt.revisionId(),receipt.revisionId());
            assertThat(publications.operationResult(rollback).receipt()).isEqualTo(rolled);
            Users.as(actor(893,7483,Set.of("tenant:brand:view","tenant:brand:publish")),()->{assertThatThrownBy(()->publications.operationResult(rollback)).isInstanceOf(AccessDeniedException.class);return null;});return null;
        });
    }
}
