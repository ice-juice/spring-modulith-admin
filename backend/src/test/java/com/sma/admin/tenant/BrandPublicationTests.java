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
class BrandPublicationTests {
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    @Autowired FileService files;
    @Autowired BrandReferenceMapper references;
    @Autowired BrandRevisionMapper revisions;
    private static final Set<String> EDIT=Set.of("tenant:brand:view","tenant:brand:edit");
    private static final Set<String> PUBLISH=Set.of("tenant:brand:view","tenant:brand:publish","tenant:brand:rollback");
    private static SmaPrincipal actor(long user,long tenant,Set<String> permissions) { return new SmaPrincipal(user,"brand-"+user,tenant,"admin",null,false,DataAccess.own(),permissions,"UTC"); }
    private static String operation() { return UUID.randomUUID().toString(); }
    private static BrandContent logo(String name,long id) {
        var base=BrandContent.defaults(name);return new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),base.appearance(),Map.of(BrandContent.AssetSlot.LOGO_LIGHT,id),Map.of());
    }
    private static byte[] png() {
        try { var image=new java.awt.image.BufferedImage(24,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(2,2,0xff123456);
            var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray(); }
        catch(java.io.IOException failure) { throw new AssertionError(failure); }
    }

    @Test void independentPublisherUsesDraftReferenceAndSnapshotsSurviveEditsAndRollback() {
        var editor=actor(801,7401,EDIT);var publisher=actor(802,7401,PUBLISH);
        long file=Users.as(editor,()->{var uploaded=files.uploadImage(new MockMultipartFile("file","logo.png","image/png",png()));drafts.save(0,logo("First",uploaded.id()));
            assertThatThrownBy(()->publications.publish(operation(),1,null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->files.delete(uploaded.id())).isInstanceOf(BizException.class);return uploaded.id();});
        String key=operation();var first=Users.as(publisher,()->publications.publish(key,1,null));
        Users.as(editor,()->drafts.save(1,BrandContent.defaults("Second")));
        var second=Users.as(publisher,()->{
            assertThat(publications.publish(key,1,null)).isEqualTo(first);
            assertThatThrownBy(()->publications.publish(key,2,first.revisionId())).isInstanceOf(BizException.class);
            return publications.publish(operation(),2,first.revisionId());
        });
        Users.as(actor(803,7401,PUBLISH),()->{assertThatThrownBy(()->publications.publish(key,1,null)).isInstanceOf(BizException.class);return null;});
        Users.as(actor(802,7401,Set.of("tenant:brand:view")),()->{assertThatThrownBy(()->publications.publish(key,1,null)).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(editor,()->{assertThatThrownBy(()->files.delete(file)).isInstanceOf(BizException.class);return null;});
        var rolled=Users.as(publisher,()->{
            var result=publications.rollback(operation(),first.revisionId(),second.revisionId());
            assertThat(result.revision()).isEqualTo(3);assertThat(result.sourceRevisionId()).isEqualTo(first.revisionId());
            assertThat(publications.get(second.revisionId()).content().identity().systemName()).isEqualTo("Second");
            assertThat(drafts.get().content().identity().systemName()).isEqualTo("Second");return result;
        });
        try(var entry=TenantEntryScope.open(new TenantEntry(7401,100,"t7401.test","admin",1))) {
            assertThat(publications.published()).get().extracting(r->r.content().identity().systemName()).isEqualTo("First");
            var image=publications.publicImage(rolled.revisionId(),BrandContent.AssetSlot.LOGO_LIGHT);
            assertThat(image.width()).isEqualTo(24);assertThat(image.height()).isEqualTo(16);
            assertThat(image.bytes()).isEqualTo(publications.publicImage(first.revisionId(),BrandContent.AssetSlot.LOGO_LIGHT).bytes());
        }
        try(var entry=TenantEntryScope.open(new TenantEntry(7402,101,"t7402.test","admin",1))) {
            assertThatThrownBy(()->publications.publicImage(first.revisionId(),BrandContent.AssetSlot.LOGO_LIGHT)).isInstanceOf(BizException.class);
        }
    }

    @Test void draftCannotReferencePrivateOrForeignFilesOrExposePrivateBytes() {
        long file=Users.as(actor(810,7410,EDIT),()->files.uploadImage(new MockMultipartFile("file","private.png","image/png",png())).id());
        Users.as(actor(811,7410,EDIT),()->{assertThatThrownBy(()->drafts.save(0,logo("Unauthorized",file))).isInstanceOf(AccessDeniedException.class);
            assertThat(references.selectCount(null)).isZero();return null;});
        Users.as(actor(810,7411,EDIT),()->{assertThatThrownBy(()->drafts.save(0,logo("Foreign",file))).isInstanceOf(BizException.class);return null;});
        Users.as(actor(810,7410,EDIT),()->{drafts.save(0,logo("Draft only",file));return null;});
        try(var entry=TenantEntryScope.open(new TenantEntry(7410,100,"t7410.test","admin",1))) {
            assertThat(publications.published()).isEmpty();assertThatThrownBy(()->publications.publicImage(file,BrandContent.AssetSlot.LOGO_LIGHT)).isInstanceOf(BizException.class);
        }
        Users.as(actor(810,7410,EDIT),()->{drafts.save(1,BrandContent.defaults("Removed"));files.delete(file);return null;});
    }

    @Test void publishIsSerializedAndFailedValidationCannotMovePointer() throws Exception {
        var editor=actor(820,7420,EDIT);var publisher=actor(821,7420,PUBLISH);
        Users.as(editor,()->drafts.save(0,BrandContent.defaults("Race")));
        var gate=new java.util.concurrent.CountDownLatch(1);var won=new java.util.concurrent.atomic.AtomicInteger();var lost=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->executor.submit(()->{
                gate.await();return Users.as(publisher,()->{try{publications.publish(operation(),1,null);won.incrementAndGet();}
                    catch(BizException conflict){assertThat(conflict.getErrorCode()).isEqualTo(TenantErrorCode.STALE);lost.incrementAndGet();}return null;});
            })).toList();gate.countDown();for(var task:tasks)task.get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(won.get()).isEqualTo(1);assertThat(lost.get()).isEqualTo(1);
        Users.as(publisher,()->{assertThat(revisions.selectCount(null)).isEqualTo(1);
            var before=drafts.get().publishedRevisionId();assertThatThrownBy(()->publications.rollback(operation(),Long.MAX_VALUE,before)).isInstanceOf(BizException.class);
            assertThat(drafts.get().publishedRevisionId()).isEqualTo(before);return null;});
    }

    @Test void simultaneousRetryOfOneOperationCreatesExactlyOnePublishedRevision() throws Exception {
        var editor=actor(830,7430,EDIT);var publisher=actor(831,7430,PUBLISH);String operation=operation();
        Users.as(editor,()->drafts.save(0,BrandContent.defaults("One operation")));
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var jobs=java.util.stream.IntStream.range(0,2).mapToObj(i->executor.submit(()->{
                gate.await();return Users.as(publisher,()->publications.publish(operation,1,null));
            })).toList();gate.countDown();
            assertThat(jobs.get(0).get(30,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(jobs.get(1).get(30,java.util.concurrent.TimeUnit.SECONDS));
        }
        Users.as(publisher,()->{assertThat(revisions.selectCount(null)).isEqualTo(1);return null;});
    }
}
