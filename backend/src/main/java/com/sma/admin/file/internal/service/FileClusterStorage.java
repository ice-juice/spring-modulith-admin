package com.sma.admin.file.internal.service;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.sma.admin.common.deployment.SharedStorageWitness;
import com.sma.admin.common.security.TenantEntryScope;

/** Small host witnesses for existing roots, never an alternative upload or file-read path. */
@Component @EnableConfigurationProperties(FileClusterProperties.class)
public class FileClusterStorage implements SharedStorageWitness,AutoCloseable {
    private record Owned(String resource,Scope scope){}
    private final Map<String,Path> roots;private final FileClusterProperties settings;private final ExecutorService worker;
    private final Map<Owned,String> owned=new ConcurrentHashMap<>();
    @Autowired
    public FileClusterStorage(FileProperties legacy,StorageSettings managed,FileClusterProperties settings){
        this(legacy,managed,settings,new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new SynchronousQueue<>(),Thread.ofPlatform().daemon().name("sma-storage-witness-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    FileClusterStorage(FileProperties legacy,StorageSettings managed,FileClusterProperties settings,ExecutorService worker){
        var map=new TreeMap<String,Path>();map.put("file.legacy",Path.of(legacy.directory()).toAbsolutePath().normalize());
        managed.localRoots().forEach((alias,directory)->map.put("file.local."+alias,Path.of(directory).toAbsolutePath().normalize()));
        if(!map.keySet().containsAll(settings.sharedResources()))throw new IllegalArgumentException("Shared file declaration references an unknown resource");
        this.roots=Map.copyOf(map);this.settings=settings;this.worker=worker;
    }
    @Override public List<Resource> resources(){host();return roots.keySet().stream().sorted().map(id->new Resource(id,settings.sharedResources().contains(id))).toList();}
    @Override public String publish(String resource,Scope scope,byte[] challenge){
        validate(resource,scope);if(challenge==null||challenge.length!=32)throw new IllegalArgumentException("Storage witness requires a 32-byte challenge");
        byte[] bytes=challenge.clone();String expected=digest(bytes);var key=new Owned(resource,scope);
        return execute(()->{
            Path path=path(resource,scope,true);interrupted();
            if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
                if(!matches(path,expected))throw unavailable();return expected;
            }
            try(var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                owned.put(key,expected);
                try(var lock=channel.tryLock()){
                    if(lock==null)throw unavailable();var buffer=ByteBuffer.wrap(bytes);
                    while(buffer.hasRemaining()){interrupted();channel.write(buffer);}channel.force(true);regular(path);
                }
            }catch(FileAlreadyExistsException raced){if(!matches(path,expected))throw unavailable();}
            return expected;
        });
    }
    @Override public boolean verify(String resource,Scope scope,String expectedDigest){
        validate(resource,scope);digestShape(expectedDigest);
        return execute(()->{Path path=path(resource,scope,false);return path!=null&&Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&matches(path,expectedDigest);});
    }
    @Override public void retire(String resource,Scope scope,String expectedDigest){
        validate(resource,scope);digestShape(expectedDigest);var key=new Owned(resource,scope);
        if(!expectedDigest.equals(owned.get(key)))return;
        execute(()->{
            Path path=path(resource,scope,false);
            if(path==null||!Files.exists(path,LinkOption.NOFOLLOW_LINKS)){owned.remove(key,expectedDigest);return null;}
            // Never delete changed, partial or busy evidence under the guise of a cache cleanup.
            if(!matches(path,expectedDigest))throw unavailable();regular(path);Files.delete(path);owned.remove(key,expectedDigest);return null;
        });
    }
    private Path path(String resource,Scope scope,boolean create)throws IOException{
        Path root=roots.get(resource);
        // A missing mount must not become an automatically-created local fallback directory.
        if(!directories(root,false))throw unavailable();
        Path parent=root.resolve(".sma-cluster").resolve(digest(scope.group().getBytes(StandardCharsets.UTF_8)))
                .resolve(digest(resource.getBytes(StandardCharsets.UTF_8))).resolve(scope.nodeId());
        if(!parent.normalize().startsWith(root)||!directories(parent,create))return null;
        return parent.resolve(scope.processId()+".probe");
    }
    private static boolean directories(Path directory,boolean create)throws IOException{
        Path current=directory.getRoot();if(current==null)throw unavailable();
        for(Path segment:directory){interrupted();current=current.resolve(segment);
            if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){
                if(!create)return false;try{Files.createDirectory(current);}catch(FileAlreadyExistsException raced){/* Check the winner. */}
            }
            var attrs=Files.readAttributes(current,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory()||attrs.isSymbolicLink()||attrs.isOther()||!current.toRealPath().equals(current.toAbsolutePath().normalize()))throw unavailable();
        }
        return true;
    }
    private static void regular(Path path)throws IOException{
        var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||attrs.isOther())throw unavailable();
        if(Files.getFileStore(path).supportsFileAttributeView("unix")){
            var links=Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS);if(links instanceof Number count&&count.longValue()!=1)throw unavailable();
        }
    }
    private static boolean matches(Path path,String expected)throws IOException{
        regular(path);
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            FileLock lock;try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){return false;}if(lock==null)return false;
            try(lock){
                if(channel.size()!=32)return false;var bytes=ByteBuffer.allocate(33);int count;
                while(bytes.hasRemaining()&&(count=channel.read(bytes))!=-1){interrupted();if(count==0)Thread.onSpinWait();}
                if(bytes.position()!=32)return false;regular(path);
                boolean matches=MessageDigest.isEqual(digest(Arrays.copyOf(bytes.array(),32)).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));
                if(matches)channel.force(true);return matches;
            }
        }
    }
    private void validate(String resource,Scope scope){
        host();Objects.requireNonNull(scope);
        if(resource==null||!roots.containsKey(resource)||!settings.sharedResources().contains(resource))throw new IllegalArgumentException("Storage resource is not explicitly shared");
    }
    private static void digestShape(String digest){if(digest==null||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid storage witness digest");}
    private static void host(){
        if(SecurityContextHolder.getContext().getAuthentication()!=null||TenantEntryScope.isHttpRequest()||RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)
            throw new AccessDeniedException("Storage witnesses require trusted host execution");
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Storage witness I/O requires no database transaction");
    }
    private <T>T execute(Callable<T> action){
        Future<T> result;try{result=worker.submit(action);}catch(RejectedExecutionException busy){throw unavailable();}
        try{return result.get(settings.probeTimeout().toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){result.cancel(true);Thread.currentThread().interrupt();throw unavailable();}
        catch(TimeoutException timeout){result.cancel(true);throw unavailable();}
        catch(ExecutionException failure){throw unavailable();}
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Storage witness interrupted");}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static IllegalStateException unavailable(){return new IllegalStateException("Shared storage witness unavailable");}
    @Override @PreDestroy public void close(){worker.shutdownNow();}
}
