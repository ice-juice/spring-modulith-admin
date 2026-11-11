package com.sma.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.ProtocolException;

/** Docker/compatible engine is a deployment prerequisite. Missing engine or image fails; never falls back to native execution. */
public final class ContainerRunner implements IsolatedCommands {
    public record Mount(Path source,String destination,boolean readOnly) {}
    private final WorkerSettings settings;
    public ContainerRunner(WorkerSettings settings) { this.settings=settings; }
    public List<String> command(String name,String image,String entrypoint,List<String> arguments,List<Mount> mounts,boolean updateNetwork) throws IOException {
        Checks.require(Files.isRegularFile(settings.dockerExecutable(),LinkOption.NOFOLLOW_LINKS),"container-engine-unavailable");
        Checks.require(image.equals(settings.scannerImage()) || image.equals(settings.javaImage()),"untrusted-container-image");
        Checks.require(!updateNetwork || image.equals(settings.scannerImage()),"build-network-forbidden");
        var args=new ArrayList<>(List.of(settings.dockerExecutable().toAbsolutePath().toString(),"run","--rm","--pull=never","--name",name,
                "--read-only","--cap-drop=ALL","--security-opt=no-new-privileges:true","--network",updateNetwork?"bridge":"none",
                "--memory=2g","--memory-swap=2g","--cpus=2","--pids-limit=64","--user",settings.containerUser(),
                "--tmpfs","/tmp:rw,noexec,nosuid,size=64m","--tmpfs","/build:rw,nosuid,size=2g,mode=1777",
                "--env","GRYPE_DB_CACHE_DIR=/db","--env","GRYPE_DB_AUTO_UPDATE=false","--env","GRYPE_CHECK_FOR_APP_UPDATE=false"));
        for(var mount:mounts) {
            Path source=mount.source().toAbsolutePath().normalize();
            Checks.require(Files.exists(source,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(source) && !source.toString().contains(",")
                    && mount.destination().matches("/(input|output|db|sdk)"),"invalid-container-mount");
            Checks.require(source.toRealPath().equals(source),"linked-container-mount");
            Checks.require(mount.readOnly() || (image.equals(settings.scannerImage()) && mount.destination().equals("/db") && updateNetwork),"writable-build-mount-forbidden");
            args.add("--mount");args.add("type=bind,src="+source+",dst="+mount.destination()+(mount.readOnly()?",readonly":""));
        }
        if(entrypoint!=null) {Checks.require(List.of("javac","jar","java").contains(entrypoint),"invalid-container-entrypoint");args.add("--entrypoint");args.add(entrypoint);}
        args.add(image);args.addAll(arguments);return List.copyOf(args);
    }
    public void run(String image,String entrypoint,List<String> arguments,List<Mount> mounts,boolean updateNetwork,Duration timeout,Path log) throws IOException,InterruptedException {
        execute(image,entrypoint,arguments,mounts,updateNetwork,timeout,log,4*1024*1024);
    }
    @Override public byte[] runBinary(String image,String entrypoint,List<String> arguments,List<Mount> mounts,Duration timeout,Path log,long maximumBytes)throws IOException,InterruptedException {
        return execute(image,entrypoint,arguments,mounts,false,timeout,log,maximumBytes);
    }
    private byte[] execute(String image,String entrypoint,List<String> arguments,List<Mount> mounts,boolean updateNetwork,Duration timeout,Path log,long maximumBytes)throws IOException,InterruptedException {
        Checks.require(timeout.toSeconds()>0 && timeout.toSeconds()<=600 && maximumBytes>0 && maximumBytes<=1024L*1024*1024,"invalid-worker-limits");
        String name="sma-worker-"+UUID.randomUUID();
        var builder=new ProcessBuilder(command(name,image,entrypoint,arguments,mounts,updateNetwork));
        var inherited=new java.util.HashMap<>(builder.environment());builder.environment().clear();
        for(String key:List.of("SystemRoot","WINDIR","PATH","TEMP","TMP","DOCKER_HOST","DOCKER_CONTEXT","DOCKER_CONFIG","XDG_RUNTIME_DIR"))
            if(inherited.containsKey(key))builder.environment().put(key,inherited.get(key));
        var process=builder.start();long deadline=System.nanoTime()+timeout.toNanos();
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var output=new java.io.ByteArrayOutputStream();
            var stdout=pool.submit(() -> {try(var input=process.getInputStream()){copyBounded(input,output,maximumBytes);}return null;});
            var stderr=pool.submit(() -> {try(var input=process.getErrorStream();var errors=Files.newOutputStream(log)){copyBounded(input,errors,4*1024*1024);}return null;});
            try {
                while(!process.waitFor(200,TimeUnit.MILLISECONDS)) {
                    Checks.require(System.nanoTime()<deadline,"worker-timeout");
                    if(stdout.isDone())await(stdout,deadline);if(stderr.isDone())await(stderr,deadline);
                }
                await(stdout,deadline);await(stderr,deadline);
                Checks.require(process.exitValue()==0,"isolated-command-failed");return output.toByteArray();
            } finally {
                // Close pipes and the client even if Docker cleanup fails. Executors cannot wait forever on inherited pipes.
                try {
                    var cleanup=new ProcessBuilder(settings.dockerExecutable().toAbsolutePath().toString(),"rm","--force",name);
                    cleanup.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
                    cleanup.environment().clear();cleanup.environment().putAll(builder.environment());
                    var remover=cleanup.start();try{if(!remover.waitFor(10,TimeUnit.SECONDS))remover.destroyForcibly();}finally{if(remover.isAlive())remover.destroyForcibly();}
                } finally {
                    process.destroyForcibly();process.getInputStream().close();process.getErrorStream().close();stdout.cancel(true);stderr.cancel(true);
                }
            }
        }
    }
    static void copyBounded(java.io.InputStream input,java.io.OutputStream output,long maximum)throws IOException {
        byte[] buffer=new byte[8192];long count=0;int read;
        while((read=input.read(buffer))!=-1){Checks.require(read<=maximum-count,"worker-output-limit-exceeded");output.write(buffer,0,read);count+=read;}
    }
    private static void await(java.util.concurrent.Future<?> task,long deadline)throws IOException,InterruptedException {
        try {task.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
        catch(java.util.concurrent.TimeoutException timeout){throw new ProtocolException("worker-timeout",timeout);}
        catch(java.util.concurrent.ExecutionException failure){if(failure.getCause() instanceof ProtocolException protocol)throw protocol;throw new IOException("container-output-failed",failure.getCause());}
    }
}
