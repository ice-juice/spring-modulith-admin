package com.sma.admin.defense.internal.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.sma.admin.common.security.SecuritySignal;
import tools.jackson.databind.json.JsonMapper;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Single-owner encrypted outbox. Worker-thread primitive only; no HTTP or scheduling dependency. */
final class EncryptedSignalSpool implements AutoCloseable {
    enum Offer { STORED, DUPLICATE, CONFLICT, CAPACITY }
    record Snapshot(int records,long bytes) {}
    record Item(UUID eventId,boolean priority) {}
    /** Preserve the classification at capture; an incompatible future classifier cannot silently replay old files. */
    private record Envelope(int schema,SecuritySignal signal,int ruleVersion,SecuritySignal.Category category,
            SecuritySignal.Severity severity,SecuritySignal.Outcome outcome,SecuritySignal.Assessment assessment) {
        static Envelope of(SecuritySignal signal){var fact=signal.fact();return new Envelope(signal.metadata()==null&&fact!=SecuritySignal.Fact.UNMAPPED_REQUEST_OBSERVED?1:2,signal,fact.ruleVersion(),fact.category(),fact.severity(),fact.outcome(),fact.assessment());}
    }
    private static final int MAGIC=0x53444531, HEADER=4+12, MAX_PAYLOAD=2048, MAX_FRAME=HEADER+MAX_PAYLOAD+16;
    private final Path directory;private final Object directoryKey;
    private final int maximumRecords,reservedRecords;private final long maximumBytes,reservedBytes;
    private final byte[] key;private final JsonMapper json;private final SecureRandom random=new SecureRandom();
    private final FileChannel lockChannel;private final FileLock lock;
    private final Map<UUID,Entry> entries=new HashMap<>();
    private long bytes;private boolean closed;
    private record Entry(Path path,int size,boolean priority) {}

    EncryptedSignalSpool(Path directory,byte[] key,int maximumRecords,long maximumBytes,int reservedRecords,long reservedBytes,JsonMapper json)throws IOException{
        if(key==null||key.length!=32||maximumRecords<1||maximumRecords>100000||maximumBytes<MAX_FRAME||maximumBytes>1024L*1024*1024
                ||reservedRecords<0||reservedRecords>=maximumRecords||reservedBytes<0||reservedBytes>=maximumBytes)
            throw new IllegalArgumentException("Invalid security spool bounds");
        this.directory=directory.toAbsolutePath().normalize();this.key=key.clone();this.maximumRecords=maximumRecords;this.maximumBytes=maximumBytes;
        this.reservedRecords=reservedRecords;this.reservedBytes=reservedBytes;this.json=Objects.requireNonNull(json);
        FileChannel opened=null;FileLock acquired=null;
        try{
            checkAncestors(this.directory);Files.createDirectories(this.directory);checkAncestors(this.directory);
            var attrs=Files.readAttributes(this.directory,BasicFileAttributes.class,NOFOLLOW_LINKS);
            if(!attrs.isDirectory())throw failure();this.directoryKey=attrs.fileKey();
            Path lockPath=this.directory.resolve(".lock");
            if(Files.exists(lockPath,NOFOLLOW_LINKS)&&!Files.isRegularFile(lockPath,NOFOLLOW_LINKS))throw failure();
            opened=FileChannel.open(lockPath,CREATE,WRITE,NOFOLLOW_LINKS);
            try{acquired=opened.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){throw failure();}
            if(acquired==null)throw failure();this.lockChannel=opened;this.lock=acquired;
            recover();
        }catch(IOException|RuntimeException error){
            if(acquired!=null)try{acquired.release();}catch(IOException ignored){}
            if(opened!=null)try{opened.close();}catch(IOException ignored){}
            Arrays.fill(this.key,(byte)0);throw error;
        }
    }
    synchronized Offer offer(SecuritySignal signal)throws IOException{
        check();Objects.requireNonNull(signal);byte[] plain=json.writeValueAsBytes(Envelope.of(signal));
        try{
            if(plain.length>MAX_PAYLOAD)throw failure();
            var prior=entries.get(signal.eventId());
            if(prior!=null){byte[] old=decrypt(prior.path,signal.eventId());try{return MessageDigest.isEqual(old,plain)?Offer.DUPLICATE:Offer.CONFLICT;}finally{Arrays.fill(old,(byte)0);}}
            boolean priority=priority(signal);int size=HEADER+plain.length+16;
            if(entries.size()>=maximumRecords||bytes+size>maximumBytes||!priority&&(entries.size()>=maximumRecords-reservedRecords||bytes+size>maximumBytes-reservedBytes))return Offer.CAPACITY;
            Path pending=path(signal.eventId(),".pending"),ready=path(signal.eventId(),".ready");
            byte[] frame=encrypt(plain,signal.eventId());
            // Do not overwrite any unknown previous attempt. Startup recovery owns pending-file reconciliation.
            try(var output=FileChannel.open(pending,CREATE_NEW,WRITE,NOFOLLOW_LINKS)){
                var buffer=ByteBuffer.wrap(frame);while(buffer.hasRemaining())output.write(buffer);output.force(true);
            }
            Files.move(pending,ready,StandardCopyOption.ATOMIC_MOVE);
            entries.put(signal.eventId(),new Entry(ready,frame.length,priority));bytes+=frame.length;
            return Offer.STORED;
        }catch(IOException error){closedForWrites();throw error;}
        finally{Arrays.fill(plain,(byte)0);}
    }
    // Any incomplete filesystem operation makes this instance unusable until an explicit reopen/reconcile.
    private boolean failed;
    private void closedForWrites(){failed=true;}
    synchronized List<Item> items()throws IOException{
        check();return entries.entrySet().stream().map(e->new Item(e.getKey(),e.getValue().priority))
                .sorted(Comparator.comparing(Item::priority).reversed().thenComparing(item->item.eventId().toString())).toList();
    }
    synchronized SecuritySignal read(UUID id)throws IOException{
        check();var entry=entries.get(id);if(entry==null)throw failure();byte[] plain=decrypt(entry.path,id);
        try{return decode(plain,id);}finally{Arrays.fill(plain,(byte)0);}
    }
    synchronized void acknowledge(UUID id)throws IOException{
        check();var entry=entries.get(id);if(entry==null)throw failure();
        // Validate ciphertext again before deletion: do not acknowledge a replaced/corrupt artifact.
        byte[] plain=decrypt(entry.path,id);try{decode(plain,id);}finally{Arrays.fill(plain,(byte)0);}
        try{Files.delete(entry.path);entries.remove(id);bytes-=entry.size;}
        catch(IOException error){closedForWrites();throw error;}
    }
    synchronized Snapshot snapshot()throws IOException{check();return new Snapshot(entries.size(),bytes);}
    private void recover()throws IOException{
        var pending=new ArrayList<Path>();long scanned=0;
        try(var files=Files.newDirectoryStream(directory)){
            for(Path file:files){
                if(++scanned>maximumRecords+1L)throw failure();
                String name=file.getFileName().toString();if(name.equals(".lock"))continue;
                if(!name.matches("[0-9a-f-]{36}\\.(ready|pending)"))throw failure();
                UUID id=parseId(name.substring(0,36));byte[] plain=decrypt(file,id);
                SecuritySignal signal;try{signal=decode(plain,id);}finally{Arrays.fill(plain,(byte)0);}
                int size=Math.toIntExact(Files.size(file));bytes+=size;
                if(bytes>maximumBytes||entries.putIfAbsent(id,new Entry(file,size,priority(signal)))!=null)throw failure();
                if(name.endsWith(".pending"))pending.add(file);
            }
        }
        // Validate the entire bounded directory before mutating any recovered artifact.
        for(Path file:pending){UUID id=parseId(file.getFileName().toString().substring(0,36));Path ready=path(id,".ready");
            Files.move(file,ready,StandardCopyOption.ATOMIC_MOVE);var old=entries.get(id);entries.put(id,new Entry(ready,old.size,old.priority));}
    }
    private byte[] encrypt(byte[] plain,UUID id)throws IOException{
        byte[] nonce=new byte[12];random.nextBytes(nonce);
        try{
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(id));
            return ByteBuffer.allocate(HEADER+plain.length+16).putInt(MAGIC).put(nonce).put(cipher.doFinal(plain)).array();
        }catch(GeneralSecurityException error){throw failure();}
    }
    private byte[] decrypt(Path file,UUID id)throws IOException{
        if(!Files.isRegularFile(file,NOFOLLOW_LINKS))throw failure();
        try(var channel=FileChannel.open(file,READ,NOFOLLOW_LINKS)){
            long length=channel.size();if(length<HEADER+16||length>MAX_FRAME)throw failure();
            var frame=ByteBuffer.allocate((int)length);while(frame.hasRemaining())if(channel.read(frame)<0)throw failure();
            if(channel.size()!=length)throw failure();frame.flip();if(frame.getInt()!=MAGIC)throw failure();
            byte[] nonce=new byte[12];frame.get(nonce);byte[] ciphertext=new byte[frame.remaining()];frame.get(ciphertext);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(id));return cipher.doFinal(ciphertext);
        }catch(GeneralSecurityException error){throw failure();}
    }
    private SecuritySignal decode(byte[] plain,UUID id)throws IOException{
        try{var envelope=json.readValue(plain,Envelope.class);var signal=envelope.signal();
            if(signal==null||!signal.eventId().equals(id)||!envelope.equals(Envelope.of(signal)))throw failure();return signal;}
        catch(RuntimeException malformed){throw failure();}
    }
    private static byte[] aad(UUID id){return ("sma:defense:spool:v1:"+id).getBytes(StandardCharsets.US_ASCII);}
    private static UUID parseId(String value)throws IOException{try{var id=UUID.fromString(value);if(!id.toString().equals(value))throw failure();return id;}catch(IllegalArgumentException bad){throw failure();}}
    private static boolean priority(SecuritySignal signal){return signal.fact().severity()!=SecuritySignal.Severity.LOW;}
    private Path path(UUID id,String suffix){return directory.resolve(id+suffix);}
    private void check()throws IOException{
        if(closed||failed||!lock.isValid())throw failure();checkAncestors(directory);
        var attrs=Files.readAttributes(directory,BasicFileAttributes.class,NOFOLLOW_LINKS);
        if(!attrs.isDirectory()||!Objects.equals(attrs.fileKey(),directoryKey))throw failure();
    }
    private static void checkAncestors(Path path)throws IOException{
        for(Path current=path;current!=null;current=current.getParent())if(Files.exists(current,NOFOLLOW_LINKS)){
            var attributes=Files.readAttributes(current,BasicFileAttributes.class,NOFOLLOW_LINKS);
            if(attributes.isSymbolicLink()||attributes.isOther()||!current.toRealPath().equals(current.toRealPath(NOFOLLOW_LINKS)))throw failure();
        }
    }
    private static IOException failure(){return new IOException("Security spool unavailable or integrity check failed");}
    @Override public synchronized void close()throws IOException{
        if(closed)return;closed=true;Arrays.fill(key,(byte)0);
        try{lock.release();}finally{lockChannel.close();}
    }
}
