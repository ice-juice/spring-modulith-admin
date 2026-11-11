package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Internal process transport, not an OS sandbox. Production callers must supply the reviewed isolation launcher.
 * Does not log command lines, input or child output. No shell expansion or inherited Java/Docker option environment.
 */
public final class BoundedInspectionProcess implements AutoCloseable {
    public enum Reason { BUSY, START, IO, OUTPUT_LIMIT, DEADLINE, INTERRUPTED }
    public static final class Failure extends RuntimeException {
        private final Reason reason;
        Failure(Reason reason){super("Inspection process failed: "+reason);this.reason=reason;}
        public Reason reason(){return reason;}
    }
    public record Result(int exitCode,byte[] output){
        public Result{output=output.clone();}@Override public byte[] output(){return output.clone();}
        @Override public String toString(){return "InspectionProcessResult[REDACTED]";}
    }
    private final Semaphore slots;private final ExecutorService workers;private final Set<Process> active=ConcurrentHashMap.newKeySet(),retained=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public BoundedInspectionProcess(int maximumConcurrent){
        if(maximumConcurrent<1 || maximumConcurrent>8)throw new IllegalArgumentException("Invalid inspection process concurrency");
        slots=new Semaphore(maximumConcurrent);int threads=maximumConcurrent*3;
        workers=new ThreadPoolExecutor(threads,threads,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(threads),
                Thread.ofPlatform().daemon().name("file-inspection-io-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    public Result execute(List<String> command,Path directory,byte[] input,Duration deadline){
        if(command==null || command.isEmpty() || command.size()>64 || command.stream().anyMatch(arg->arg==null || arg.length()>32768 || arg.indexOf('\0')>=0)
                || !Path.of(command.getFirst()).isAbsolute() || directory==null || input==null || input.length>InspectionProtocol.MESSAGE_BYTES
                || deadline==null || deadline.isZero() || deadline.isNegative() || deadline.compareTo(Duration.ofMinutes(30))>0)throw new IllegalArgumentException("Invalid inspection invocation");
        if(closed)throw new Failure(Reason.BUSY);
        for(var previous:retained)if(!previous.isAlive() && retained.remove(previous)){active.remove(previous);slots.release();}
        if(!slots.tryAcquire())throw new Failure(Reason.BUSY);
        Process process=null;var futures=new ArrayList<Future<?>>();long started=System.nanoTime();
        try{
            var builder=new ProcessBuilder(List.copyOf(command)).directory(directory.toFile());var env=builder.environment();env.clear();
            for(String key:List.of("SystemRoot","WINDIR")){String value=System.getenv(key);if(value!=null)env.put(key,value);}
            env.put("TMP",directory.toAbsolutePath().toString());env.put("TEMP",directory.toAbsolutePath().toString());
            process=builder.start();active.add(process);final Process child=process;
            Future<byte[]> stdout=workers.submit(()->capture(child,child.getInputStream()));futures.add(stdout);
            Future<byte[]> stderr=workers.submit(()->capture(child,child.getErrorStream()));futures.add(stderr);
            futures.add(workers.submit(()->{try(var sink=child.getOutputStream()){sink.write(input);sink.flush();}return null;}));
            while(process.isAlive()){
                for(var future:futures)if(future.isDone())get(future,started,deadline);
                long left=left(started,deadline);if(left<=0)throw new Failure(Reason.DEADLINE);
                process.waitFor(Math.min(left,TimeUnit.MILLISECONDS.toNanos(50)),TimeUnit.NANOSECONDS);
            }
            for(var future:futures)get(future,started,deadline);
            return new Result(process.exitValue(),stdout.get());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new Failure(Reason.INTERRUPTED);}
         catch(RejectedExecutionException busy){throw new Failure(Reason.BUSY);}
         catch(IOException start){throw new Failure(Reason.START);}
         catch(ExecutionException failure){if(failure.getCause() instanceof Failure known)throw known;throw new Failure(Reason.IO);}
        finally{
            if(process!=null){stop(process);for(var future:futures)future.cancel(true);}
            // An unkillable child keeps its admission slot. Never keep spawning work over an unknown live process.
            if(process==null || !process.isAlive()){if(process!=null)active.remove(process);slots.release();}
            else retained.add(process);
        }
    }
    private Object get(Future<?> future,long started,Duration deadline)throws InterruptedException,ExecutionException{
        long left=left(started,deadline);if(left<=0)throw new Failure(Reason.DEADLINE);
        try{return future.get(left,TimeUnit.NANOSECONDS);}catch(TimeoutException expired){throw new Failure(Reason.DEADLINE);}
    }
    private byte[] capture(Process child,InputStream input)throws IOException{
        try(input;var output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(output.size()+count>InspectionProtocol.MESSAGE_BYTES){child.destroyForcibly();throw new Failure(Reason.OUTPUT_LIMIT);}output.write(buffer,0,count);}
            return output.toByteArray();
        }
    }
    private long left(long started,Duration deadline){return deadline.toNanos()-(System.nanoTime()-started);}
    private void stop(Process process){
        if(process.isAlive()){
            process.descendants().limit(256).forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();
            try{process.waitFor(2,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        }
        for(var stream:List.of(process.getInputStream(),process.getErrorStream(),process.getOutputStream()))try{stream.close();}catch(IOException ignored){/* Do not leak diagnostics. */}
    }
    @Override public void close(){closed=true;for(var process:active)stop(process);workers.shutdownNow();}
}
