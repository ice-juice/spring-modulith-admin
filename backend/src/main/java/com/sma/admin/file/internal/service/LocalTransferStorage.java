package com.sma.admin.file.internal.service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import com.sma.admin.common.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.deployment.DeploymentActivity;
import com.sma.admin.common.deployment.DeploymentTaskTracker;
import com.sma.admin.file.FileErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable local bytes with bounded workers and buffers. Directory aliases are deployment-owned, not request parameters. */
@Component @EnableConfigurationProperties(LocalTransferProperties.class)
public class LocalTransferStorage implements AutoCloseable, DeploymentActivity {
    /** Only executor rejection before work starts carries this proof; it is safe to release the unused task claim. */
    public static class NotStarted extends BizException {public NotStarted(){super(CommonErrorCode.SERVICE_UNAVAILABLE);}}
    public record ObjectRef(LocalStorageLocation.Location location,String uploadId,String name){
        public ObjectRef{
            if(location==null || location.tenantId()<=0 || location.profileId()<=0 || uploadId==null || !uploadId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || name==null || !name.matches("(?:part-[0-9]{5}-[a-f0-9-]{36}|frozen-[a-f0-9-]{36})\\.blob"))throw new IllegalArgumentException("Invalid managed local object reference");
        }
        @Override public String toString(){return "ManagedLocalObject[REDACTED]";}
    }
    public record Stored(ObjectRef object,long size,String checksum){}
    public record Expected(ObjectRef object,long size,String checksum){}
    public enum Presence { ABSENT, BUSY, MATCH, MISMATCH }
    public record Inspection(Presence presence,Stored stored){}
    private final LocalTransferProperties settings;private final ExecutorService workers;
    private final DeploymentTaskTracker activity;
    public LocalTransferStorage(LocalTransferProperties settings){
        this.settings=settings;activity=new DeploymentTaskTracker(settings.maximumConcurrent());
        workers=new ThreadPoolExecutor(settings.maximumConcurrent(),settings.maximumConcurrent(),0,TimeUnit.SECONDS,new SynchronousQueue<>(),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-local-transfer-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    @Override public String id(){return "file.local-transfer";}
    @Override public DeploymentActivity.Snapshot snapshot(){return activity.snapshot();}
    public Stored write(ObjectRef object,InputStream input,long expectedSize,String expectedChecksum){
        validate(object,expectedSize,expectedChecksum);Objects.requireNonNull(input);
        return execute(()->writeBytes(object,input,expectedSize,expectedChecksum),input);
    }
    /** An existing object is never overwritten. A busy writer must finish before a reader can reconcile it. */
    public Inspection inspect(ObjectRef object,long expectedSize,String expectedChecksum){
        validate(object,expectedSize,expectedChecksum);return execute(()->inspectBytes(object,expectedSize,expectedChecksum),null);
    }
    /** Frozen target is exclusively created and built from the exact, locked, measured part objects. */
    public Stored freeze(ObjectRef target,List<Expected> parts,long expectedSize,String checksum){
        validate(target,expectedSize,checksum);if(!target.name().startsWith("frozen-") || parts==null || parts.isEmpty() || parts.size()>10000)throw invalid();
        var copy=List.copyOf(parts);long size=0;
        for(var part:copy){validate(part.object(),part.size(),part.checksum());if(!part.object().location().equals(target.location()) || !part.object().uploadId().equals(target.uploadId()) || !part.object().name().startsWith("part-"))throw invalid();size=Math.addExact(size,part.size());}
        if(size!=expectedSize)throw invalid();return execute(()->freezeBytes(target,copy,expectedSize,checksum),null);
    }
    private Stored writeBytes(ObjectRef object,InputStream input,long size,String checksum)throws IOException{
        Path path=path(object,true);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw state();free(path.getParent(),size);
        try(input;var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=channel.lock()){
            var digest=digest();long total=0;byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer,0,(int)Math.min(buffer.length,size-total+1)))!=-1){
                interrupted();if(count==0)continue;total=Math.addExact(total,count);if(total>size)throw invalid();digest.update(buffer,0,count);write(channel,buffer,count);
            }
            if(total!=size || !checksum.equals(HexFormat.of().formatHex(digest.digest())))throw invalid();channel.force(true);regular(path);return new Stored(object,size,checksum);
        }
        // Any failure leaves evidence in the immutable claim path for reconciliation/cleanup. Never silently grant another write.
    }
    private Inspection inspectBytes(ObjectRef object,long size,String checksum)throws IOException{
        Path path=path(object,false);if(path==null || !Files.exists(path,LinkOption.NOFOLLOW_LINKS))return new Inspection(Presence.ABSENT,null);regular(path);
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            FileLock lock;try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){return new Inspection(Presence.BUSY,null);}if(lock==null)return new Inspection(Presence.BUSY,null);
            try(lock){var measured=measure(object,channel,size,null);boolean match=measured.size()==size && measured.checksum().equals(checksum);
                if(match)channel.force(true);return new Inspection(match?Presence.MATCH:Presence.MISMATCH,measured);}
        }
    }
    private Stored freezeBytes(ObjectRef target,List<Expected> parts,long size,String checksum)throws IOException{
        Path path=path(target,true);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw state();free(path.getParent(),size);
        try(var output=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var targetLock=output.lock()){
            var digest=digest();long total=0;
            for(var part:parts){
                Path source=path(part.object(),false);if(source==null)throw invalid();regular(source);
                try(var input=FileChannel.open(source,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                    FileLock lock;try{lock=input.tryLock();}catch(OverlappingFileLockException busy){throw state();}if(lock==null)throw state();
                    try(lock){var partDigest=digest();long count=0;var buffer=ByteBuffer.allocate(65536);int read;
                        while((read=input.read(buffer))!=-1){interrupted();if(read==0)continue;count+=read;total+=read;if(count>part.size() || total>size)throw invalid();
                            partDigest.update(buffer.array(),0,read);digest.update(buffer.array(),0,read);buffer.flip();while(buffer.hasRemaining())output.write(buffer);buffer.clear();}
                        if(count!=part.size() || !HexFormat.of().formatHex(partDigest.digest()).equals(part.checksum()))throw invalid();
                    }
                }
            }
            if(total!=size || !checksum.equals(HexFormat.of().formatHex(digest.digest())))throw invalid();output.force(true);regular(path);return new Stored(target,total,checksum);
        }
    }
    private Stored measure(ObjectRef object,FileChannel channel,long upper,MessageDigest supplied)throws IOException{
        var digest=supplied==null?digest():supplied;long size=0;var buffer=ByteBuffer.allocate(65536);int read;
        while((read=channel.read(buffer))!=-1){interrupted();if(read==0)continue;size+=read;digest.update(buffer.array(),0,read);buffer.clear();if(size>upper)break;}
        return new Stored(object,size,HexFormat.of().formatHex(digest.digest()));
    }
    private Path path(ObjectRef object,boolean create)throws IOException{
        Path root=Path.of(object.location().directory()).toAbsolutePath().normalize();
        // Every ancestor must be an actual directory. The deployment account exclusively controls these roots and parents.
        if(!directories(root,create))return null;
        Path task=root.resolve("tenants").resolve(Long.toString(object.location().tenantId())).resolve("profiles").resolve(Long.toString(object.location().profileId()));
        String prefix=object.location().prefix();if(prefix!=null && !prefix.isEmpty()){
            if(!prefix.matches("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*"))throw invalid();
            for(String segment:prefix.split("/")){if(segment.equals(".") || segment.equals(".."))throw invalid();task=task.resolve(segment);}
        }
        task=task.resolve("tasks").resolve(object.uploadId()).normalize();if(!task.startsWith(root))throw invalid();if(!directories(task,create))return null;
        Path path=task.resolve(object.name());if(Files.isSymbolicLink(path))throw invalid();return path;
    }
    private boolean directories(Path directory,boolean create)throws IOException{
        Path current=directory.getRoot();if(current==null)throw invalid();
        for(Path segment:directory){current=current.resolve(segment);
            if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){
                if(!create)return false;try{Files.createDirectory(current);}catch(FileAlreadyExistsException raced){/* Verify the winning directory below. */}
            }
            var attrs=Files.readAttributes(current,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther())throw invalid();
            if(!current.toRealPath().equals(current.toAbsolutePath().normalize()))throw invalid();
        }
        return true;
    }
    private void regular(Path path)throws IOException{
        var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther())throw invalid();
        if(Files.getFileStore(path).supportsFileAttributeView("unix")){Object links=Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS);if(links instanceof Number count && count.longValue()!=1)throw invalid();}
    }
    private void free(Path directory,long bytes)throws IOException{long usable=Files.getFileStore(directory).getUsableSpace();if(usable<bytes || usable-bytes<settings.minimumFreeBytes())throw new BizException(FileErrorCode.UPLOAD_LIMIT);}
    private void write(FileChannel channel,byte[] bytes,int size)throws IOException{var buffer=ByteBuffer.wrap(bytes,0,size);while(buffer.hasRemaining()){interrupted();channel.write(buffer);}}
    private void validate(ObjectRef object,long size,String checksum){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Local transfer I/O requires no database transaction");
        ActorContext.requireTenant(object.location().tenantId());if(size<1 || size>20L*1024*1024*1024 || checksum==null || !checksum.matches("[0-9a-f]{64}"))throw invalid();
    }
    private <T>T execute(Callable<T> work,Closeable input){
        Future<T> future;
        try{future=activity.submit(workers,work);}catch(RejectedExecutionException busy){closeQuietly(input);throw new NotStarted();}
        try{return future.get(settings.deadline().toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){future.cancel(true);closeQuietly(input);Thread.currentThread().interrupt();throw new BizException(FileErrorCode.STORAGE);}
        catch(TimeoutException timeout){future.cancel(true);closeQuietly(input);throw new BizException(FileErrorCode.STORAGE);}
        catch(CancellationException cancelled){closeQuietly(input);throw new BizException(FileErrorCode.STORAGE);}
        catch(ExecutionException failure){closeQuietly(input);if(failure.getCause() instanceof BizException rejected)throw rejected;if(failure.getCause() instanceof IllegalStateException bug)throw bug;throw new BizException(FileErrorCode.STORAGE);}
    }
    private void closeQuietly(Closeable input){if(input!=null)try{input.close();}catch(IOException ignored){/* Preserve the transfer failure and immutable evidence. */}}
    private void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Local transfer interrupted");}
    private MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private BizException invalid(){return new BizException(FileErrorCode.INVALID_UPLOAD);}
    private BizException state(){return new BizException(FileErrorCode.UPLOAD_STATE);}
    @Override @PreDestroy public void close(){activity.close();workers.shutdownNow();}
}
