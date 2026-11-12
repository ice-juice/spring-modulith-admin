package com.sma.admin.defense.internal.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.LongAdder;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.SecuritySignal;
import tools.jackson.databind.json.JsonMapper;

/** One actual execution thread; every I/O unit is admitted before starting and holds its permit to completion. */
final class SecurityCollectionWorker implements AutoCloseable {
    private final DefenseCollectionProperties settings;private final SecurityCollectionQueue queue;private final DeploymentWork admission;
    private final Function<SecuritySignal,SecurityEventStore.Result> database;private final JsonMapper json;private final LongSupplier clock;
    private EncryptedSignalSpool spool;private UUID cursor;private long retryAt;private boolean retryPending,closed;
    private volatile boolean stopping;
    private volatile boolean available;private volatile int diskRecords;private volatile long diskBytes;
    final LongAdder persisted=new LongAdder(),stored=new LongAdder(),duplicates=new LongAdder(),expired=new LongAdder(),conflicts=new LongAdder(),capacity=new LongAdder(),failures=new LongAdder();
    SecurityCollectionWorker(DefenseCollectionProperties settings,SecurityCollectionQueue queue,DeploymentWork admission,
            Function<SecuritySignal,SecurityEventStore.Result> database,JsonMapper json,LongSupplier clock){
        this.settings=settings;this.queue=queue;this.admission=admission;this.database=database;this.json=json;this.clock=clock;
    }
    synchronized void cycle(){
        DeploymentEvidence.requireHost();if(closed||stopping||retryPending&&clock.getAsLong()-retryAt<0)return;retryPending=false;
        for(int i=0;i<settings.batch();i++){
            if(stopping)return;
            var accepted=admission.tryStart();if(accepted.isEmpty())return;
            try(var permit=accepted.orElseThrow()){
                if(spool==null)open();
                boolean progressed=false;var pending=queue.peek();
                if(pending!=null){
                    var result=spool.offer(pending);
                    switch(result){
                        case STORED,DUPLICATE->{queue.persisted(pending);persisted.increment();progressed=true;}
                        case CAPACITY->capacity.increment();
                        case CONFLICT->{conflicts.increment();defer();return;}
                    }
                }
                var items=spool.items();updateSize();
                if(items.isEmpty()){if(!progressed)return;continue;}
                // Rotate through a bounded snapshot so one tenant's capacity cannot starve all later records.
                int index=0;if(cursor!=null){for(int j=0;j<items.size();j++)if(items.get(j).eventId().equals(cursor)){index=(j+1)%items.size();break;}}
                UUID id=items.get(index).eventId();cursor=id;var signal=spool.read(id);
                SecurityEventStore.Result result;
                try{result=database.apply(signal);}catch(RuntimeException unavailable){failures.increment();defer();return;}
                switch(result){
                    case STORED->{stored.increment();spool.acknowledge(id);progressed=true;}
                    case DUPLICATE->{duplicates.increment();spool.acknowledge(id);progressed=true;}
                    case EXPIRED->{expired.increment();spool.acknowledge(id);progressed=true;}
                    case CONFLICT->conflicts.increment();
                    case CAPACITY->capacity.increment();
                }
                updateSize();if(!progressed&&items.size()==1){defer();return;}
            }catch(IOException|RuntimeException failed){
                failures.increment();available=false;defer();closeSpool();return;
            }
        }
        // Even a full-capacity multi-record backlog must not spin continuously.
        if(diskRecords>0)defer();
    }
    private void open()throws IOException{
        Path path=Path.of(settings.keyFile()).toAbsolutePath().normalize();
        for(Path current=path;current!=null;current=current.getParent())if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)){
            var attrs=Files.readAttributes(current,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(attrs.isSymbolicLink()||attrs.isOther()||!current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS)))throw new IOException("Security buffer key unavailable");
        }
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Security buffer key unavailable");
        byte[] key=new byte[32];
        try(var input=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            if(input.size()!=32)throw new IOException("Invalid security buffer key size");var buffer=ByteBuffer.wrap(key);
            while(buffer.hasRemaining())if(input.read(buffer)<0)throw new IOException("Security buffer key unavailable");
            if(input.size()!=32)throw new IOException("Security buffer key changed");
            byte[] derived;
            try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));
                derived=mac.doFinal(("sma:defense:node-spool:v1:"+settings.scope()).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            catch(java.security.GeneralSecurityException unavailable){throw new IOException("Security buffer key unavailable");}
            try{spool=new EncryptedSignalSpool(Path.of(settings.directory()),derived,settings.diskRecords(),settings.diskBytes(),settings.diskRecords()/4,settings.diskBytes()/4,json);}
            finally{Arrays.fill(derived,(byte)0);}
            available=true;updateSize();
        }finally{Arrays.fill(key,(byte)0);}
    }
    private void updateSize()throws IOException{var state=spool.snapshot();diskRecords=state.records();diskBytes=state.bytes();}
    private void defer(){retryAt=clock.getAsLong()+settings.retryDelay().toNanos();retryPending=true;}
    boolean available(){return available;}int diskRecords(){return diskRecords;}long diskBytes(){return diskBytes;}
    private void closeSpool(){if(spool!=null){try{spool.close();}catch(IOException ignored){}spool=null;}}
    void stop(){stopping=true;}
    @Override public synchronized void close(){closed=true;available=false;closeSpool();}
}
