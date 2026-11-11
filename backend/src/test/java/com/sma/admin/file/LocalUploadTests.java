package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.persistence.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.*;

@IntegrationTest
class LocalUploadTests {
    private static Path keyFile,storageRoot;private static final int MIB=1024*1024;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-local-upload-",".keys");storageRoot=Files.createTempDirectory("sma-local-upload-files-");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.file.storage.local-roots.default",()->storageRoot.toString());registry.add("sma.file.tasks.part-bytes",()->MIB);
        registry.add("sma.file.local-transfer.minimum-free-bytes",()->0);registry.add("sma.file.local-transfer.deadline",()->"30s");
    }
    @AfterAll static void cleanup()throws Exception{
        if(keyFile!=null)Files.deleteIfExists(keyFile);
        if(storageRoot!=null)try(var files=Files.walk(storageRoot)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
    }
    @Autowired StorageProfileService profiles;@Autowired StorageSceneService scenes;@Autowired LocalUploadCoordinator coordinator;
    @MockitoSpyBean UploadTaskStore tasks;@MockitoSpyBean LocalTransferStorage storage;@Autowired UploadTaskMapper rows;@Autowired UploadPartMapper parts;
    @Autowired TransactionTemplate tx;
    private <T>T as(long tenant,Supplier<T> work){return Users.as(new SmaPrincipal(903,"local-uploader",tenant,"admin",null,false,DataAccess.none(),Set.of("file:storage:manage","file:scene:manage","file:scene:publish","file:file:upload"),"UTC"),work);}
    private StorageProfileService.View fixture(long tenant){return as(tenant,()->{
        var profile=profiles.create(new StorageProfileService.Input("local","Local",StorageProvider.LOCAL,"default",null,null,null,"assets",null,null,null,null,Set.of("file"),true,true));
        var scene=scenes.create(new StorageSceneService.Input("file.attachment","Files",new StorageSceneService.Configuration(Set.of(profile.id()),profile.id(),Set.of(FileFormat.TEXT),Set.of(UploadMode.SERVER,UploadMode.MULTIPART),5L*MIB,30)));
        scenes.publish(scene.id(),1);return profile;
    });}
    private String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private UploadTaskStore.View initiate(long tenant,byte[] value,String checksum){return as(tenant,()->tasks.initiate(new UploadTaskStore.Input(UUID.randomUUID().toString(),
            new FileSceneAccess("file","file.attachment",FileSceneAccess.Action.UPLOAD,null,null,null),null,UploadMode.MULTIPART,"notes.txt",FileFormat.TEXT,value.length,checksum)));}
    private UploadTaskStore.View part(long tenant,String id,int number,byte[] value){return as(tenant,()->coordinator.part(id,number,value.length,hash(value),new ByteArrayInputStream(value)));}
    private UploadTaskEntity row(long tenant,String id){return as(tenant,()->rows.selectOne(new LambdaQueryWrapper<UploadTaskEntity>().eq(UploadTaskEntity::getPublicId,id)));}
    private Path directory(long tenant,long profile,String task){return storageRoot.resolve("tenants").resolve(Long.toString(tenant)).resolve("profiles").resolve(Long.toString(profile)).resolve("assets/tasks").resolve(task);}
    @Test void actualPartsResumeAndFreezeIntoAnIndependentScanningOnlyObject()throws Exception{
        long tenant=1421;var profile=fixture(tenant);byte[] bytes=new byte[2*MIB+17];new Random(1).nextBytes(bytes);var task=initiate(tenant,bytes,hash(bytes));
        for(int i=0;i<3;i++){byte[] chunk=Arrays.copyOfRange(bytes,i*MIB,Math.min(bytes.length,(i+1)*MIB));part(tenant,task.uploadId(),i+1,chunk);}
        var replay=part(tenant,task.uploadId(),1,Arrays.copyOf(bytes,MIB));assertThat(replay.receivedParts()).isEqualTo(3);
        var scanning=as(tenant,()->coordinator.complete(task.uploadId(),replay.version()));assertThat(scanning.state()).isEqualTo("SCANNING");assertThat(as(tenant,()->coordinator.complete(task.uploadId(),1))).isEqualTo(scanning);
        var persisted=row(tenant,task.uploadId());Path frozen=directory(tenant,profile.id(),task.uploadId()).resolve(persisted.getFrozenReceipt());assertThat(Files.readAllBytes(frozen)).containsExactly(bytes);
        var first=as(tenant,()->parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,persisted.getId()).eq(UploadPartEntity::getPartNumber,1)));
        Files.write(directory(tenant,profile.id(),task.uploadId()).resolve(first.getStorageReceipt()),new byte[]{9});assertThat(Files.readAllBytes(frozen)).containsExactly(bytes);
        verify(storage,times(3)).write(any(),any(),anyLong(),anyString());verify(storage,times(1)).freeze(any(),anyList(),anyLong(),anyString());
    }
    @Test void confirmedClosedPartialGetsOneNewImmutableAttemptWithoutDiscardingOtherParts()throws Exception{
        long tenant=1422;var profile=fixture(tenant);byte[] bytes=new byte[MIB+5];new Random(2).nextBytes(bytes);var task=initiate(tenant,bytes,hash(bytes));byte[] first=Arrays.copyOf(bytes,MIB),last=Arrays.copyOfRange(bytes,MIB,bytes.length);
        part(tenant,task.uploadId(),2,last);InputStream broken=new ByteArrayInputStream(first){int calls;
            @Override public synchronized int read(byte[] buffer,int offset,int length){if(calls++>1)throw new java.io.UncheckedIOException(new IOException("simulated disconnect"));return super.read(buffer,offset,Math.min(length,100));}};
        assertThatThrownBy(()->as(tenant,()->coordinator.part(task.uploadId(),1,first.length,hash(first),broken))).isInstanceOf(BizException.class);
        var resumed=part(tenant,task.uploadId(),1,first);assertThat(resumed.receivedParts()).isEqualTo(2);var taskRow=row(tenant,task.uploadId());
        var stored=as(tenant,()->parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,taskRow.getId()).eq(UploadPartEntity::getPartNumber,1)));assertThat(stored.getAttempts()).isEqualTo(2);
        try(var paths=Files.list(directory(tenant,profile.id(),task.uploadId()))){assertThat(paths.filter(p->p.getFileName().toString().startsWith("part-")).count()).isEqualTo(3);}
        assertThat(as(tenant,()->coordinator.complete(task.uploadId(),resumed.version())).state()).isEqualTo("SCANNING");
    }
    @Test void lostPartAndFreezeAcknowledgementsAreRecoveredWithoutRepeatingWrites(){
        long tenant=1423;fixture(tenant);byte[] bytes="accepted durable bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);var task=initiate(tenant,bytes,hash(bytes));
        UploadTaskStore target=AopTestUtils.getUltimateTargetObject(tasks);
        doThrow(new IllegalStateException("lost database acknowledgement")).when(target).confirmPart(any(),anyLong(),anyString(),anyString());
        assertThatThrownBy(()->part(tenant,task.uploadId(),1,bytes)).isInstanceOf(IllegalStateException.class);
        doCallRealMethod().when(target).confirmPart(any(),anyLong(),anyString(),anyString());var recovered=part(tenant,task.uploadId(),1,bytes);assertThat(recovered.receivedParts()).isEqualTo(1);
        verify(storage,times(1)).write(any(),any(),anyLong(),anyString());
        doThrow(new IllegalStateException("lost freeze acknowledgement")).when(target).confirmFrozen(any(),anyLong(),anyString(),anyString());
        assertThatThrownBy(()->as(tenant,()->coordinator.complete(task.uploadId(),recovered.version()))).isInstanceOf(IllegalStateException.class);
        doCallRealMethod().when(target).confirmFrozen(any(),anyLong(),anyString(),anyString());assertThat(as(tenant,()->coordinator.complete(task.uploadId(),recovered.version())).state()).isEqualTo("SCANNING");
        verify(storage,times(1)).freeze(any(),anyList(),anyLong(),anyString());
    }
    @Test void concurrentReplayObservesBusyWriterAndNeverOverwritesItsPart()throws Exception{
        long tenant=1424;fixture(tenant);byte[] bytes="concurrent part".getBytes(java.nio.charset.StandardCharsets.UTF_8);var task=initiate(tenant,bytes,hash(bytes));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);InputStream blocked=new ByteArrayInputStream(bytes){
            @Override public synchronized int read(byte[] b,int o,int l){entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return -1;}return super.read(b,o,l);}};
        try(var executor=Executors.newSingleThreadExecutor()){
            var first=executor.submit(()->as(tenant,()->coordinator.part(task.uploadId(),1,bytes.length,hash(bytes),blocked)));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var duplicate=part(tenant,task.uploadId(),1,bytes);assertThat(duplicate.receivedParts()).isZero();release.countDown();assertThat(first.get(10,TimeUnit.SECONDS).receivedParts()).isEqualTo(1);
        }finally{release.countDown();}
        verify(storage,times(1)).write(any(),any(),anyLong(),anyString());
    }
    @Test void finalChecksumAndChangedStoredPartCannotReachScanningAndIoNeverJoinsOuterTransaction()throws Exception{
        long tenant=1425;var profile=fixture(tenant);byte[] bytes="real content".getBytes(java.nio.charset.StandardCharsets.UTF_8);var wrong=initiate(tenant,bytes,"a".repeat(64));var uploaded=part(tenant,wrong.uploadId(),1,bytes);
        assertThatThrownBy(()->as(tenant,()->coordinator.complete(wrong.uploadId(),uploaded.version()))).isInstanceOf(BizException.class);assertThat(row(tenant,wrong.uploadId()).getState()).isEqualTo("VERIFYING");
        var task=initiate(tenant,bytes,hash(bytes));var ready=part(tenant,task.uploadId(),1,bytes);var persisted=row(tenant,task.uploadId());
        var part=as(tenant,()->parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,persisted.getId())));
        Files.write(directory(tenant,profile.id(),task.uploadId()).resolve(part.getStorageReceipt()),new byte[]{1});
        assertThatThrownBy(()->as(tenant,()->coordinator.complete(task.uploadId(),ready.version()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(tenant,()->tx.execute(status->coordinator.part(task.uploadId(),1,bytes.length,hash(bytes),new ByteArrayInputStream(bytes))))).isInstanceOf(IllegalStateException.class);
    }
    @Test void explicitPreExecutionRejectionCanRetryButNeverResetsAnUnknownWrite(){
        long tenant=1426;fixture(tenant);byte[] bytes="retry after capacity rejection".getBytes(java.nio.charset.StandardCharsets.UTF_8);var task=initiate(tenant,bytes,hash(bytes));
        doThrow(new LocalTransferStorage.NotStarted()).when(storage).write(any(),any(),anyLong(),anyString());
        assertThatThrownBy(()->part(tenant,task.uploadId(),1,bytes)).isInstanceOf(LocalTransferStorage.NotStarted.class);
        assertThat(as(tenant,()->tasks.parts(task.uploadId(),0,100)).getFirst().state()).isEqualTo("RETRYABLE");
        doCallRealMethod().when(storage).write(any(),any(),anyLong(),anyString());var stored=part(tenant,task.uploadId(),1,bytes);assertThat(stored.receivedParts()).isEqualTo(1);
        doThrow(new LocalTransferStorage.NotStarted()).when(storage).freeze(any(),anyList(),anyLong(),anyString());
        assertThatThrownBy(()->as(tenant,()->coordinator.complete(task.uploadId(),stored.version()))).isInstanceOf(LocalTransferStorage.NotStarted.class);assertThat(row(tenant,task.uploadId()).getFreezeClaim()).isNull();
        doCallRealMethod().when(storage).freeze(any(),anyList(),anyLong(),anyString());assertThat(as(tenant,()->coordinator.complete(task.uploadId(),stored.version())).state()).isEqualTo("SCANNING");
        var unknown=initiate(tenant,bytes,hash(bytes));as(tenant,()->tasks.beginPart(unknown.uploadId(),1,bytes.length,hash(bytes)));
        var pending=part(tenant,unknown.uploadId(),1,bytes);assertThat(pending.receivedParts()).isZero();assertThat(as(tenant,()->tasks.parts(unknown.uploadId(),0,100)).getFirst().state()).isEqualTo("RECEIVING");
    }
}
