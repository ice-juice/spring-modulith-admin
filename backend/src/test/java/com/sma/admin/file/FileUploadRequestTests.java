package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class FileUploadRequestTests {
    @Autowired FileUploadRequests uploads;
    @MockitoSpyBean FileUploadStore store;
    @MockitoSpyBean FileService files;
    @MockitoSpyBean TenantEntryQuery tenants;

    @Test void oneIntentWritesOneFileAndChangedBytesNameOrModeConflict(){
        Users.as(actor(79801),()->{
            String key=key();var first=uploads.upload(key,file("one"),false);var again=uploads.upload(key,file("one"),false);
            assertThat(again.file().id()).isEqualTo(first.file().id());
            assertThat(uploads.byKey(key).state()).isEqualTo("SUCCEEDED");
            assertThatThrownBy(()->uploads.upload(key,file("two"),false)).isInstanceOfSatisfying(RequestRejected.class,e->assertThat(e.reason()).isEqualTo(RequestRejected.Reason.CONFLICT));
            assertThatThrownBy(()->uploads.upload(key,new MockMultipartFile("file","other.txt","text/plain","one".getBytes(StandardCharsets.UTF_8)),false)).isInstanceOf(RequestRejected.class);
            assertThatThrownBy(()->uploads.upload(key,file("one"),true)).isInstanceOf(RequestRejected.class);
            var distinct=uploads.upload(key(),file("one"),false);
            assertThat(distinct.file().id()).isNotEqualTo(first.file().id());
            assertThat(files.page(new PageQuery(1,20,null,null),null,null).total()).isEqualTo(2);
            return null;
        });
    }
    @Test void acceptedWorkCanExplicitlyResumeButStartedUnknownWorkCannot(){
        Users.as(actor(79802),()->{
            String key=key();
            doThrow(new DataAccessResourceFailureException("before start")).when(store).start(anyString());
            assertThatThrownBy(()->uploads.upload(key,file("one"),false)).isInstanceOf(DataAccessResourceFailureException.class);
            var accepted=uploads.byKey(key);assertThat(accepted.state()).isEqualTo("ACCEPTED");assertThat(accepted.resumable()).isTrue();
            doCallRealMethod().when(store).start(anyString());
            assertThat(uploads.upload(key,file("one"),false).receipt().state()).isEqualTo("SUCCEEDED");

            String uncertain=key();
            doAnswer(invocation->{failAfterCommit();return invocation.callRealMethod();}).when(store).start(anyString());
            assertThatThrownBy(()->uploads.upload(uncertain,file("two"),false)).isInstanceOf(DataAccessResourceFailureException.class);
            doCallRealMethod().when(store).start(anyString());
            var running=uploads.byKey(uncertain);assertThat(running.resumable()).isFalse();
            assertThat(store.finish(running.uploadId(),false).state()).isEqualTo("UNKNOWN");
            assertThat(uploads.upload(uncertain,file("two"),false).receipt().state()).isEqualTo("UNKNOWN");
            assertThat(files.page(new PageQuery(1,20,null,null),null,null).total()).isEqualTo(1);
            return null;
        });
    }
    @Test void lostFileCommitAcknowledgementReturnsReconciledResultWithoutSecondStorageWrite(){
        Users.as(actor(79807),()->{
            doAnswer(invocation->{failAfterCommit();return invocation.callRealMethod();})
                    .when(files).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());
            String key=key();var completed=uploads.upload(key,file("committed-before-exception"),false);
            assertThat(completed.receipt().state()).isEqualTo("SUCCEEDED");
            assertThat(uploads.upload(key,file("committed-before-exception"),false).file().id()).isEqualTo(completed.file().id());
            assertThatCode(()->files.content(completed.file().id())).doesNotThrowAnyException();
            verify(files,times(1)).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());return null;
        });
    }
    @Test void deletionBeforeReconciliationIsEvidenceOfCompletionButDoesNotRestoreTheAsset(){
        Users.as(actor(79808),()->{
            String key=key();
            doThrow(new DataAccessResourceFailureException("result update unavailable")).when(store).finish(anyString(),anyBoolean());
            assertThatThrownBy(()->uploads.upload(key,file("deleted-before-query"),false)).isInstanceOf(DataAccessResourceFailureException.class);
            long id=files.page(new PageQuery(1,20,null,null),null,null).items().getFirst().id();files.delete(id);
            var receipt=uploads.byKey(key);assertThat(receipt.state()).isEqualTo("SUCCEEDED");assertThat(receipt.fileId()).isEqualTo(id);
            assertThatThrownBy(()->uploads.upload(key,file("deleted-before-query"),false)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->files.content(id)).isInstanceOf(java.nio.file.NoSuchFileException.class);
            verify(files,times(1)).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());return null;
        });
    }
    private static void failAfterCommit(){
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){throw new DataAccessResourceFailureException("lost caller acknowledgement after real commit");}
        });
    }
    @Test void lostResultUpdateReconcilesCommittedMetadataIncludingDeletionWithoutRestoringBytes(){
        Users.as(actor(79803),()->{
            String key=key();
            doThrow(new DataAccessResourceFailureException("result store temporarily unavailable")).when(store).finish(anyString(),anyBoolean());
            assertThatThrownBy(()->uploads.upload(key,file("committed"),false)).isInstanceOf(DataAccessResourceFailureException.class);
            doCallRealMethod().when(store).finish(anyString(),anyBoolean());
            var receipt=uploads.byKey(key);assertThat(receipt.state()).isEqualTo("SUCCEEDED");
            assertThat(uploads.upload(key,file("committed"),false).file().id()).isEqualTo(receipt.fileId());
            files.delete(receipt.fileId());
            assertThat(uploads.receipt(receipt.uploadId()).state()).isEqualTo("SUCCEEDED");
            assertThatThrownBy(()->uploads.upload(key,file("committed"),false)).isInstanceOf(BizException.class);
            assertThat(files.page(new PageQuery(1,20,null,null),null,null).total()).isZero();
            verify(files,times(1)).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());
            return null;
        });
    }
    @Test void concurrentSameKeyObservesRunningWhileOnlyTheWinnerWrites()throws Exception{
        var actor=actor(79804);String key=key();Users.as(actor,()->null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        doAnswer(invocation->{calls.incrementAndGet();entered.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return invocation.callRealMethod();})
                .when(files).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->Users.as(actor,()->uploads.upload(key,file("concurrent"),false)));
            try{
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                Users.as(actor,()->{var duplicate=uploads.upload(key,file("concurrent"),false);assertThat(duplicate.file()).isNull();assertThat(duplicate.receipt().state()).isEqualTo("RUNNING");return null;});
            }finally{release.countDown();}
            assertThat(first.get(15,TimeUnit.SECONDS).receipt().state()).isEqualTo("SUCCEEDED");
        }
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void failedRasterIsFinalAndReceiptsRequireOwnerTenantPortalPermissionAndCurrentGeneration(){
        var actor=actor(79805);String key=key();
        var receipt=Users.as(actor,()->{
            assertThatThrownBy(()->uploads.upload(key,file("not an image"),true)).isInstanceOf(BizException.class);
            var failed=uploads.byKey(key);assertThat(failed.state()).isEqualTo("FAILED_FINAL");
            assertThat(uploads.upload(key,file("not an image"),true).receipt().state()).isEqualTo("FAILED_FINAL");
            assertThat(files.page(new PageQuery(1,20,null,null),null,null).total()).isZero();
            return uploads.upload(key(),file("owned"),false).receipt();
        });
        Users.as(new SmaPrincipal(999,"other",actor.tenantId(),"admin",10L,false,DataAccess.unrestricted(),actor.permissions(),"UTC"),()->{
            assertThatThrownBy(()->uploads.receipt(receipt.uploadId())).isInstanceOf(RequestRejected.class);return null;
        });
        Users.as(actor(79806),()->{assertThatThrownBy(()->uploads.receipt(receipt.uploadId())).isInstanceOf(RequestRejected.class);return null;});
        Users.as(new SmaPrincipal(actor.userId(),"other-portal",actor.tenantId(),"app",10L,false,DataAccess.unrestricted(),actor.permissions(),"UTC"),()->{
            assertThatThrownBy(()->uploads.receipt(receipt.uploadId())).isInstanceOf(RequestRejected.class);return null;
        });
        Users.as(Users.principal(actor.userId(),actor.tenantId(),DataAccess.unrestricted()),()->{
            assertThatThrownBy(()->uploads.receipt(receipt.uploadId())).isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(actor,()->{
            String pending=key();doThrow(new DataAccessResourceFailureException("before start")).when(store).start(anyString());
            assertThatThrownBy(()->uploads.upload(pending,file("generation"),false)).isInstanceOf(DataAccessResourceFailureException.class);
            doCallRealMethod().when(store).start(anyString());
            long generation=tenants.securityState(actor.tenantId()).orElseThrow().generation();
            doReturn(Optional.of(new TenantEntryQuery.SecurityState(actor.tenantId(),generation+1,true,true))).when(tenants).securityState(actor.tenantId());
            assertThatThrownBy(()->uploads.upload(pending,file("generation"),false)).isInstanceOf(AccessDeniedException.class);
            verify(files,times(2)).uploadAccepted(any(),anyLong(),anyBoolean(),anyLong(),anyString(),any());return null;
        });
    }
    static MockMultipartFile file(String value){return new MockMultipartFile("file","asset.txt","text/plain",value.getBytes(StandardCharsets.UTF_8));}
    static String key(){return UUID.randomUUID().toString();}
    static SmaPrincipal actor(long tenant){return new SmaPrincipal(101,"uploader",tenant,"admin",10L,false,DataAccess.unrestricted(),Set.of("file:file:upload","file:file:view","file:file:delete"),"UTC");}
}
