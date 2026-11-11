package com.sma.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import com.sma.extension.protocol.*;

/** Single worker process handles one job at a time; control-plane limits cap the aggregate number of workers. */
public final class ValidationWorker {
    private final WorkerSettings settings;private final ContainerRunner runner;private final GrypeAdapter scanner;private final Clock clock;
    private final WorkerClient client;private final byte[] probeBytes;private final SourceCompiler compiler;
    public ValidationWorker(WorkerSettings settings,ContainerRunner runner,GrypeAdapter scanner,Clock clock,Path probeJar,String expectedProbeHash,SourceCompiler compiler) throws IOException {
        this.settings=settings;this.runner=runner;this.scanner=scanner;this.clock=clock;this.client=new WorkerClient(settings);
        Path binary=probeJar.toAbsolutePath().normalize();this.compiler=compiler;Checks.hash(expectedProbeHash);
        Checks.require(Files.isRegularFile(binary,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(binary) && Files.size(binary)<=200*ResourcePolicy.MIB,"probe-binary-required");
        try(var input=Files.newInputStream(binary,LinkOption.NOFOLLOW_LINKS)) {probeBytes=input.readNBytes(Math.toIntExact(200*ResourcePolicy.MIB+1));}
        Checks.require(probeBytes.length<=200*ResourcePolicy.MIB && Hashes.sha256(probeBytes).equals(expectedProbeHash),"probe-binary-hash-mismatch");
    }
    public boolean once() throws IOException,InterruptedException {
        var assignment=client.claim();if(assignment==null) return false;
        Path root=settings.workDirectory().toAbsolutePath().normalize();Files.createDirectories(root);
        Checks.require(!Files.isSymbolicLink(root) && root.toRealPath().equals(root),"unsafe-worker-root");
        Path job=Files.createTempDirectory(root,"job-");
        try {
            Checks.require(assignment.job().environment().equals(settings.environment()) && clock.instant().isBefore(assignment.job().expiresAt()),"worker-job-expired");
            var disk=Files.getFileStore(root);assignment.policy().checkSpace(disk.getTotalSpace(),disk.getUsableSpace(),0,assignment.policy().expandedBytes()+assignment.bytes());
            byte[] bytes;String artifactId=assignment.job().artifactId();String kind="IMPORT";
            if(assignment.build()!=null) {
                Checks.require(compiler!=null,"source-compiler-unavailable");
                var inputs=new java.util.HashMap<String,byte[]>();
                for(var file:assignment.build().inputs()) inputs.put(file.artifactId(),client.source(assignment,file));
                var built=compiler.compile(assignment.build(),inputs,assignment.dependencies(),assignment.policy(),job);
                artifactId=client.uploadOutput(assignment.job().id(),built);bytes=built.jar();kind="BUILD";
            } else {Checks.require(assignment.name().endsWith(".jar"),"source-plan-required");bytes=client.download(assignment);}
            var snapshot=ArchiveSnapshot.inspect(bytes,assignment.policy(),assignment.dependencies());
            Path input=Files.createDirectories(job.resolve("probe-input"));Path sdk=Files.createDirectories(job.resolve("probe-sdk"));
            Files.write(input.resolve("artifact.jar"),bytes);Files.write(input.resolve("dependencies.json"),ProtocolJson.write(assignment.dependencies()));
            Files.write(sdk.resolve("worker.jar"),probeBytes);
            runner.run(settings.javaImage(),"java",List.of("-Xmx256m","-XX:MaxMetaspaceSize=128m","-XX:+DisableAttachMechanism","-cp","/sdk/worker.jar",
                    "com.sma.extension.runtime.ContractProbe","/input/artifact.jar","/input/dependencies.json"),
                    List.of(new ContainerRunner.Mount(input,"/input",true),new ContainerRunner.Mount(sdk,"/sdk",true)),false,Duration.ofSeconds(60),job.resolve("probe.log"));
            var scan=scanner.scan(snapshot,job);
            Checks.require(clock.instant().isBefore(assignment.job().expiresAt()),"worker-job-expired");
            client.complete(assignment.job().id(),new WorkerClient.Result(artifactId,snapshot.sha256(),snapshot.manifest(),scan,snapshot.inventory(),kind,clock.instant()));
            return true;
        } catch(ProtocolException failure) {
            client.fail(assignment.job().id(),failure.code());return true;
        } catch(IOException failure) {
            try {client.fail(assignment.job().id(),"worker-io-failure");}catch(Exception reporting){failure.addSuppressed(reporting);}
            throw failure;
        } finally {
            // The generated job root has been checked; walk never follows symlinks. No arbitrary path comes from a request.
            try(var paths=Files.walk(job)) {for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);}
        }
    }
}
