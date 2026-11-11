package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.file.FileErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LocalTransferStorageTests {
    @TempDir Path root;
    private LocalTransferStorage storage(){return new LocalTransferStorage(new LocalTransferProperties(2,Duration.ofSeconds(3),0L));}
    private LocalTransferStorage.ObjectRef object(String name){return new LocalTransferStorage.ObjectRef(new LocalStorageLocation.Location(1,2,"default",root.toString(),"assets"),UUID.randomUUID().toString(),name);}
    private String part(){return "part-00001-"+UUID.randomUUID()+".blob";}
    private String hash(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private <T>T job(java.util.function.Supplier<T> action){return ActorContext.withinJob(1,"local-test",action);}
    private Path path(LocalTransferStorage.ObjectRef object){return root.resolve("tenants/1/profiles/2/assets/tasks").resolve(object.uploadId()).resolve(object.name());}
    @Test void streamWriteIsExclusiveMeasuredAndTenantScoped()throws Exception{
        byte[] value="bounded-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=hash(value);var ref=object(part());
        try(var storage=storage()){
            var stored=job(()->storage.write(ref,new ByteArrayInputStream(value),value.length,hash));assertThat(stored.size()).isEqualTo(value.length);assertThat(stored.checksum()).isEqualTo(hash);
            assertThat(job(()->storage.inspect(ref,value.length,hash)).presence()).isEqualTo(LocalTransferStorage.Presence.MATCH);
            assertThatThrownBy(()->job(()->storage.write(ref,new ByteArrayInputStream(value),value.length,hash))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(FileErrorCode.UPLOAD_STATE));
            assertThatThrownBy(()->ActorContext.withinJob(9,"foreign",()->storage.inspect(ref,value.length,hash))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            TransactionSynchronizationManager.setActualTransactionActive(true);try{assertThatThrownBy(()->job(()->storage.inspect(ref,value.length,hash))).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
            assertThat(Files.readAllBytes(path(ref))).containsExactly(value);
        }
    }
    @Test void oversizedTruncatedAndChecksumFailuresKeepImmutableEvidence()throws Exception{
        byte[] value=new byte[100];Arrays.fill(value,(byte)7);String hash=hash(value);
        try(var storage=storage()){
            var truncated=object(part());assertThatThrownBy(()->job(()->storage.write(truncated,new ByteArrayInputStream(Arrays.copyOf(value,50)),100,hash))).isInstanceOf(BizException.class);
            assertThat(job(()->storage.inspect(truncated,100,hash)).presence()).isEqualTo(LocalTransferStorage.Presence.MISMATCH);
            var oversized=object(part());assertThatThrownBy(()->job(()->storage.write(oversized,new ByteArrayInputStream(new byte[101]),100,hash))).isInstanceOf(BizException.class);
            assertThat(Files.size(path(oversized))).isLessThanOrEqualTo(100);
            var corrupt=object(part());assertThatThrownBy(()->job(()->storage.write(corrupt,new ByteArrayInputStream(value),100,"a".repeat(64)))).isInstanceOf(BizException.class);
            assertThat(job(()->storage.inspect(corrupt,100,"a".repeat(64))).presence()).isEqualTo(LocalTransferStorage.Presence.MISMATCH);
        }
    }
    @Test void freezeRechecksEachPartAndCreatesIndependentImmutableSnapshot()throws Exception{
        byte[] one="hello ".getBytes(java.nio.charset.StandardCharsets.UTF_8),two="world".getBytes(java.nio.charset.StandardCharsets.UTF_8),all="hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String a=hash(one),b=hash(two),full=hash(all);var first=object(part());var second=new LocalTransferStorage.ObjectRef(first.location(),first.uploadId(),"part-00002-"+UUID.randomUUID()+".blob");
        var frozen=new LocalTransferStorage.ObjectRef(first.location(),first.uploadId(),"frozen-"+UUID.randomUUID()+".blob");
        try(var storage=storage()){
            job(()->storage.write(first,new ByteArrayInputStream(one),one.length,a));job(()->storage.write(second,new ByteArrayInputStream(two),two.length,b));
            var expected=List.of(new LocalTransferStorage.Expected(first,one.length,a),new LocalTransferStorage.Expected(second,two.length,b));
            assertThat(job(()->storage.freeze(frozen,expected,all.length,full)).checksum()).isEqualTo(full);
            Files.write(path(first),new byte[one.length]);assertThat(job(()->storage.inspect(frozen,all.length,full)).presence()).isEqualTo(LocalTransferStorage.Presence.MATCH);
            var wrong=new LocalTransferStorage.ObjectRef(first.location(),first.uploadId(),"frozen-"+UUID.randomUUID()+".blob");
            assertThatThrownBy(()->job(()->storage.freeze(wrong,expected,all.length,full))).isInstanceOf(BizException.class);
            assertThat(Files.readAllBytes(path(frozen))).containsExactly(all);
        }
    }
    @Test void writerLockPreventsReadingPartialObjectAndRejectedParallelWorkDoesNotQueue()throws Exception{
        byte[] value="ready".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=hash(value);var ref=object(part());var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        InputStream blocked=new ByteArrayInputStream(value){@Override public synchronized int read(byte[] bytes,int offset,int length){entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return -1;}return super.read(bytes,offset,length);}};
        try(var storage=storage();var caller=Executors.newSingleThreadExecutor()){
            var writer=caller.submit(()->job(()->storage.write(ref,blocked,value.length,hash)));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(job(()->storage.inspect(ref,value.length,hash)).presence()).isEqualTo(LocalTransferStorage.Presence.BUSY);release.countDown();assertThat(writer.get(3,TimeUnit.SECONDS).checksum()).isEqualTo(hash);
        }finally{release.countDown();}
        try(var storage=new LocalTransferStorage(new LocalTransferProperties(1,Duration.ofSeconds(1),0L));var caller=Executors.newSingleThreadExecutor()){
            var started=new CountDownLatch(1);var end=new CountDownLatch(1);InputStream slow=new InputStream(){public int read()throws IOException{throw new IOException("Not used");}
                public int read(byte[] b,int o,int l)throws IOException{started.countDown();try{end.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("interrupted");}return -1;}public void close(){end.countDown();}};
            var pending=caller.submit(()->job(()->storage.write(object(part()),slow,1,"a".repeat(64))));assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->job(()->storage.inspect(object(part()),1,"a".repeat(64)))).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
            assertThatThrownBy(()->pending.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);assertThat(end.getCount()).isZero();
        }
    }
    @Test void referenceGrammarAndExistingNonDirectoryParentsAreRejected()throws Exception{
        assertThatThrownBy(()->object("../outside.blob")).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(root.resolve("tenants"),"not a directory");var ref=object(part());
        try(var storage=storage()){assertThatThrownBy(()->job(()->storage.write(ref,new ByteArrayInputStream(new byte[]{1}),1,"a".repeat(64)))).isInstanceOf(BizException.class);}
        assertThat(Files.readString(root.resolve("tenants"))).isEqualTo("not a directory");
    }
    @Test void finalSymlinkIsRejectedWhenHostAllowsLinkCreation()throws Exception{
        byte[] data=new byte[]{1};String hash=hash(data);var ref=object(part());Files.createDirectories(path(ref).getParent());Path external=Files.write(root.resolve("external"),data);
        try{Files.createSymbolicLink(path(ref),external);}catch(UnsupportedOperationException|FileSystemException denied){org.junit.jupiter.api.Assumptions.abort("Host does not permit symbolic link fixture");}
        try(var storage=storage()){
            assertThatThrownBy(()->job(()->storage.inspect(ref,1,hash))).isInstanceOf(BizException.class);
            assertThatThrownBy(()->job(()->storage.write(ref,new ByteArrayInputStream(data),1,hash))).isInstanceOf(BizException.class);
        }
        assertThat(Files.readAllBytes(external)).containsExactly(data);
    }
    @Test void windowsDirectoryJunctionCannotRedirectTheTenantDirectory()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"));
        Path outside=Files.createDirectory(root.resolve("outside")),junction=root.resolve("tenants");
        var process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",junction.toString(),outside.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();String result=new String(process.getInputStream().readNBytes(4096),java.nio.charset.Charset.defaultCharset());
        assertThat(process.exitValue()).as(result).isZero();
        try(var storage=storage()){
            var ref=object(part());assertThatThrownBy(()->job(()->storage.write(ref,new ByteArrayInputStream(new byte[]{1}),1,"a".repeat(64)))).isInstanceOf(BizException.class);
            try(var files=Files.list(outside)){assertThat(files.count()).isZero();}
        }finally{Files.deleteIfExists(junction);}
    }
}
