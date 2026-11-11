package com.sma.worker;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;

/** Worker entry point. Runs only explicitly requested modes; no background credential-bearing child process. */
public final class WorkerMain {
    private WorkerMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("audit-mirror")) {AuditMirror.synchronize(URI.create(required("SMA_AUDIT_SECURITY_URL")),required("SMA_AUDIT_TOKEN"),Path.of(required("SMA_AUDIT_DIRECTORY")));return;}
        var settings=new WorkerSettings(URI.create(required("SMA_WORKER_SECURITY_URL")),required("SMA_WORKER_TOKEN"),required("SMA_WORKER_ENVIRONMENT"),
                Path.of(required("SMA_WORKER_DIRECTORY")),Path.of(required("SMA_WORKER_CONTAINER_ENGINE")),required("SMA_WORKER_SCANNER_IMAGE"),
                required("SMA_WORKER_JAVA_IMAGE"),Path.of(required("SMA_WORKER_SCANNER_DB")),required("SMA_WORKER_CONTAINER_USER"));
        var runner=new ContainerRunner(settings);var scanner=new GrypeAdapter(settings,runner,Clock.systemUTC());
        java.nio.file.Files.createDirectories(settings.scannerDatabase());
        try(var lockFile=java.nio.channels.FileChannel.open(settings.scannerDatabase().resolve("sma-worker.lock"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS);var lock=lockFile.tryLock()) {
        com.sma.extension.protocol.Checks.require(lock!=null,"scanner-database-in-use");
        if(args.length==1 && args[0].equals("refresh-database")) scanner.refreshDatabase();
        else if(args.length==1 && args[0].equals("once")) {
            var compiler=new SourceCompiler(settings,runner,Path.of(required("SMA_WORKER_SDK_JAR")),required("SMA_WORKER_SDK_SHA256"));
            var worker=new ValidationWorker(settings,runner,scanner,Clock.systemUTC(),Path.of(required("SMA_WORKER_PROBE_JAR")),required("SMA_WORKER_PROBE_SHA256"),compiler);worker.once();
        } else throw new IllegalArgumentException("Supported modes: refresh-database, once, audit-mirror");
        }
    }
    private static String required(String name) {
        String value=System.getenv(name);if(value==null || value.isBlank()) throw new IllegalArgumentException("Required worker setting: "+name);return value;
    }
}
