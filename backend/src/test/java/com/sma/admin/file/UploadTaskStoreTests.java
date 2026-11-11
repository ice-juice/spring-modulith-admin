package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.persistence.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class UploadTaskStoreTests {
    private static Path keyFile;private static final long MIB=1024*1024;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-upload-tasks-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.file.tasks.part-bytes",()->MIB);registry.add("sma.file.tasks.maximum-active",()->3);registry.add("sma.file.tasks.maximum-retained",()->5);
        registry.add("sma.file.tasks.maximum-reserved-bytes",()->12*MIB);registry.add("sma.file.tasks.maximum-concurrent-parts",()->1);
        registry.add("sma.file.tasks.per-minute",()->3);registry.add("sma.file.tasks.per-day",()->5);registry.add("sma.file.tasks.lifetime",()->"5m");
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired StorageProfileService profiles;@Autowired StorageSceneService scenes;@Autowired UploadTaskStore store;
    @Autowired UploadTaskMapper tasks;@Autowired UploadPartMapper parts;@Autowired TaskBudgetMapper budgets;@Autowired TransactionTemplate tx;
    @MockitoSpyBean FileClock clock;@MockitoSpyBean TenantEntryQuery tenants;
    private Instant now;
    @BeforeEach void time(){now=Instant.parse("2026-11-12T00:00:00Z");doAnswer(i->now).when(clock).now();}
    private SmaPrincipal actor(long tenant,long user,String portal){return new SmaPrincipal(user,"u"+user,tenant,portal,null,false,DataAccess.none(),Set.of("file:storage:manage","file:storage:view","file:scene:manage","file:scene:publish","file:scene:view","file:file:upload"),"UTC");}
    private <T>T as(long tenant,Supplier<T> work){return Users.as(actor(tenant,901,"admin"),work);}
    private StorageProfileService.View fixture(long tenant){return as(tenant,()->{
        var p=profiles.create(new StorageProfileService.Input("local","Local",StorageProvider.LOCAL,"default",null,null,null,"assets",null,null,null,null,Set.of("file"),true,true));
        var draft=scenes.create(new StorageSceneService.Input("file.attachment","Files",new StorageSceneService.Configuration(Set.of(p.id()),p.id(),Set.of(FileFormat.TEXT),Set.of(UploadMode.SERVER,UploadMode.MULTIPART),5*MIB,30)));
        scenes.publish(draft.id(),1);return p;
    });}
    private UploadTaskStore.Input input(String key,long size){return new UploadTaskStore.Input(key,new FileSceneAccess("file","file.attachment",FileSceneAccess.Action.UPLOAD,null,null,null),null,UploadMode.MULTIPART,"notes.txt",FileFormat.TEXT,size,"a".repeat(64));}
    private UploadTaskStore.View initiate(long tenant,long size){return as(tenant,()->store.initiate(input(UUID.randomUUID().toString(),size)));}
    private void error(Runnable action,FileErrorCode expected){assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(expected));}
    @Test void sameKeyHasOnePersistentTaskAndCannotChangeContentOrEscapeOwnerScope(){
        fixture(1401);fixture(1402);String key=UUID.randomUUID().toString();var input=input(key,10);var first=as(1401,()->store.initiate(input));
        assertThat(as(1401,()->store.initiate(input))).isEqualTo(first);error(()->as(1401,()->store.initiate(input(key,11))),FileErrorCode.UPLOAD_CONFLICT);
        for(var other:List.of(actor(1401,902,"admin"),actor(1401,901,"other"),actor(1402,901,"admin")))
            error(()->Users.as(other,()->store.status(first.uploadId())),FileErrorCode.UPLOAD_NOT_FOUND);
        var independent=as(1402,()->store.initiate(input));assertThat(independent.uploadId()).isNotEqualTo(first.uploadId());
        as(1401,()->{var row=tasks.selectOne(null);assertThat(row.getInputFingerprint()).doesNotContain("notes.txt");assertThat(row.getKeyHash()).doesNotContain(key);
            assertThat(budgets.selectOne(null).getReservedBytes()).isEqualTo(30);assertThat(budgets.selectOne(null).getActiveTasks()).isEqualTo(1);return null;});
    }
    @Test void partClaimsHaveSingleExecutionAndConfirmedBytesAreIdempotent(){
        fixture(1403);var upload=initiate(1403,MIB+7);as(1403,()->{
            var first=store.beginPart(upload.uploadId(),1,MIB,"b".repeat(64));assertThat(first.execute()).isTrue();
            var replay=store.beginPart(upload.uploadId(),1,MIB,"b".repeat(64));assertThat(replay.execute()).isFalse();assertThat(replay.claimId()).isEqualTo(first.claimId());
            error(()->store.beginPart(upload.uploadId(),1,MIB,"c".repeat(64)),FileErrorCode.UPLOAD_CONFLICT);
            error(()->store.beginPart(upload.uploadId(),2,7,"c".repeat(64)),FileErrorCode.UPLOAD_LIMIT);
            error(()->store.confirmPart(first,MIB,"d".repeat(64),"part-1"),FileErrorCode.UPLOAD_CONFLICT);
            var confirmed=store.confirmPart(first,MIB,"b".repeat(64),"part-1");assertThat(confirmed.receivedParts()).isEqualTo(1);
            assertThat(store.confirmPart(first,MIB,"b".repeat(64),"part-1").receivedParts()).isEqualTo(1);
            var second=store.beginPart(upload.uploadId(),2,7,"c".repeat(64));store.confirmPart(second,7,"c".repeat(64),"part-2");
            var ready=store.status(upload.uploadId());assertThat(ready.receivedBytes()).isEqualTo(MIB+7);assertThat(store.parts(upload.uploadId(),0,1)).hasSize(1);assertThat(store.parts(upload.uploadId(),1,100)).hasSize(1);
            var verifying=store.requestVerification(upload.uploadId(),ready.version());assertThat(verifying.state()).isEqualTo("VERIFYING");assertThat(verifying.state()).isNotEqualTo("READY");
            assertThat(store.requestVerification(upload.uploadId(),ready.version())).isEqualTo(verifying);error(()->store.abort(upload.uploadId(),verifying.version()),FileErrorCode.UPLOAD_STATE);return null;
        });
    }
    @Test void incompleteOrMalformedPartsCannotCompleteAndAbortDoesNotReleaseUncleanedBytes(){
        fixture(1404);var upload=initiate(1404,MIB+1);as(1404,()->{
            error(()->store.beginPart(upload.uploadId(),0,MIB,"b".repeat(64)),FileErrorCode.INVALID_UPLOAD);
            error(()->store.beginPart(upload.uploadId(),2,MIB,"b".repeat(64)),FileErrorCode.INVALID_UPLOAD);
            error(()->store.requestVerification(upload.uploadId(),1),FileErrorCode.UPLOAD_STATE);
            var claim=store.beginPart(upload.uploadId(),1,MIB,"b".repeat(64));var current=store.status(upload.uploadId());
            error(()->store.abort(upload.uploadId(),1),FileErrorCode.STORAGE_VERSION_CONFLICT);
            var aborted=store.abort(upload.uploadId(),current.version());assertThat(aborted.state()).isEqualTo("ABORTING");assertThat(store.abort(upload.uploadId(),1)).isEqualTo(aborted);
            error(()->store.confirmPart(claim,MIB,"b".repeat(64),"part-1"),FileErrorCode.UPLOAD_STATE);
            assertThat(budgets.selectOne(null).getReservedBytes()).isEqualTo((MIB+1)*3);assertThat(budgets.selectOne(null).getActiveTasks()).isEqualTo(1);return null;
        });
    }
    @Test void quotaAndCommittedAdmissionSurviveCallerRollbackAndExpiryOnlySchedulesCleanup(){
        fixture(1405);String key=UUID.randomUUID().toString();
        assertThatThrownBy(()->as(1405,()->tx.execute(status->{store.initiate(input(key,4*MIB));throw new IllegalStateException("outer rollback");}))).isInstanceOf(IllegalStateException.class);
        var task=as(1405,()->store.initiate(input(key,4*MIB)));error(()->initiate(1405,1),FileErrorCode.UPLOAD_LIMIT);
        now=now.plusSeconds(301);error(()->as(1405,()->store.beginPart(task.uploadId(),1,MIB,"b".repeat(64))),FileErrorCode.UPLOAD_EXPIRED);
        int expired=ActorContext.withinJob(1405,"maintenance",store::expire);assertThat(expired).isEqualTo(1);
        as(1405,()->{assertThat(store.status(task.uploadId()).state()).isEqualTo("EXPIRED");assertThat(budgets.selectOne(null).getReservedBytes()).isEqualTo(12*MIB);return null;});
        error(()->initiate(1405,1),FileErrorCode.UPLOAD_LIMIT);
        assertThatThrownBy(()->as(1405,store::expire)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void duplicateConcurrentInitiationsAndPartClaimsHaveOneWinner()throws Exception{
        fixture(1406);String key=UUID.randomUUID().toString();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<UploadTaskStore.View> begin=()->{start.await();return as(1406,()->store.initiate(input(key,10)));};var a=pool.submit(begin);var b=pool.submit(begin);start.countDown();
            var task=a.get(30,TimeUnit.SECONDS);assertThat(b.get(30,TimeUnit.SECONDS).uploadId()).isEqualTo(task.uploadId());
            var gate=new CountDownLatch(1);Callable<Boolean> claim=()->{gate.await();return as(1406,()->store.beginPart(task.uploadId(),1,10,"b".repeat(64)).execute());};
            var first=pool.submit(claim);var second=pool.submit(claim);gate.countDown();assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        as(1406,()->{assertThat(tasks.selectCount(null)).isEqualTo(1);assertThat(parts.selectCount(null)).isEqualTo(1);return null;});
    }
    @Test void liveTenantSceneAndProfileChangesCannotBeBypassedByExistingTaskOrKey(){
        var profile=fixture(1407);String key=UUID.randomUUID().toString();var input=input(key,10);var upload=as(1407,()->store.initiate(input));
        as(1407,()->profiles.stop(profile.id(),1,StorageProfileService.Stop.UPLOADS));
        assertThatThrownBy(()->as(1407,()->store.initiate(input))).isInstanceOf(BizException.class);assertThatThrownBy(()->as(1407,()->store.status(upload.uploadId()))).isInstanceOf(BizException.class);
        fixture(1408);var other=initiate(1408,10);doReturn(Optional.of(new TenantEntryQuery.SecurityState(1408,2,true,true))).when(tenants).securityState(1408);
        assertThatThrownBy(()->as(1408,()->store.beginPart(other.uploadId(),1,10,"b".repeat(64)))).isInstanceOf(AccessDeniedException.class);
    }
}
