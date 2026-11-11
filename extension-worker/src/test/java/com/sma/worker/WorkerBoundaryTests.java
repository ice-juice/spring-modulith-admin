package com.sma.worker;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.extension.protocol.*;

class WorkerBoundaryTests {
    @TempDir Path dir;
    @Test void engineCannotUseFloatingImagesRootOrNetworkForPluginCode() throws Exception {
        Path engine=Files.createFile(dir.resolve("engine.exe"));Path input=Files.createDirectory(dir.resolve("input"));
        String image="registry.example/tool@sha256:"+"a".repeat(64),javaImage="registry.example/jdk@sha256:"+"b".repeat(64);
        var config=new WorkerSettings(URI.create("https://security.example"),"x".repeat(43),"production",dir,engine,image,javaImage,dir.resolve("db"),"65532:65532");
        var runner=new ContainerRunner(config);
        var command=runner.command("sma-test",javaImage,"java",List.of("-version"),List.of(new ContainerRunner.Mount(input,"/input",true)),false);
        assertThat(command).contains("--pull=never","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges:true","--memory=2g","--cpus=2","--pids-limit=64");
        assertThat(command.get(command.indexOf("--network")+1)).isEqualTo("none");
        assertThat(command).noneMatch(v -> v.contains(config.token()));
        assertThatThrownBy(() -> runner.command("sma-test",javaImage,"java",List.of(),List.of(),true)).hasMessage("build-network-forbidden");
        assertThatThrownBy(() -> runner.command("sma-test",javaImage,"java",List.of(),List.of(new ContainerRunner.Mount(input,"/output",false)),false)).hasMessage("writable-build-mount-forbidden");
        assertThatThrownBy(() -> new WorkerSettings(config.securityUrl(),config.token(),config.environment(),dir,engine,"grype:latest",javaImage,dir,"65532:65532")).hasMessage("pinned-image-required");
        assertThatThrownBy(() -> new WorkerSettings(config.securityUrl(),config.token(),config.environment(),dir,engine,image,javaImage,dir,"0:0")).hasMessage("nonroot-container-user-required");
    }
    @Test void boundedContainerStreamsStopBeforeWritingExcessBytes()throws Exception {
        var output=new ByteArrayOutputStream();
        ContainerRunner.copyBounded(new java.io.ByteArrayInputStream(new byte[8192]),output,8192);assertThat(output.size()).isEqualTo(8192);
        var rejected=new ByteArrayOutputStream();
        assertThatThrownBy(() -> ContainerRunner.copyBounded(new java.io.ByteArrayInputStream(new byte[16384]),rejected,9000)).hasMessage("worker-output-limit-exceeded");
        assertThat(rejected.size()).isEqualTo(8192);
    }
    @Test void missingOrUnparseableScannerOutputNeverBecomesSuccessfulScan() throws Exception {
        var snapshot=snapshot(Map.of());var now=Instant.parse("2026-11-11T00:00:00Z");
        assertThatThrownBy(() -> GrypeAdapter.parse("{}".getBytes(),snapshot,now,now)).hasMessage("scanner-results-incomplete");
        var valid=ProtocolJson.write(Map.of("descriptor",Map.of("name","grype","version","fixture","db",Map.of()),"matches",List.of()));
        var report=GrypeAdapter.parse(valid,snapshot,now,now);assertThat(report.complete()).isTrue();assertThat(report.inventorySha256()).containsExactly(snapshot.sha256());
        var failed=ProtocolJson.write(Map.of("descriptor",Map.of("name","grype","version","fixture","db",Map.of("error","not available")),"matches",List.of()));
        assertThatThrownBy(() -> GrypeAdapter.parse(failed,snapshot,now,now)).hasMessage("scanner-database-error");
    }
    @Test void nestedPathsAndUnknownEmbeddedArchivesFailBeforeAnyClassIsLoaded() throws Exception {
        assertThatThrownBy(() -> snapshot(Map.of("lib/unknown.jar",new byte[]{1,2,3}))).hasMessage("undeclared-nested-dependency");
        assertThatThrownBy(() -> snapshot(Map.of("../evil.class",new byte[]{1}))).hasMessage("unsafe-archive-path");
        assertThatThrownBy(() -> snapshot(Map.of("META-INF/services/javax.annotation.processing.Processor","evil".getBytes()))).hasMessage("unsupported-archive-layout");
    }
    private ArchiveSnapshot snapshot(Map<String,byte[]> extra) throws Exception {
        var manifest=new ArtifactManifest(1,"example","1.0.0",1,"example.Handler",List.of(new ArtifactManifest.Entry("execute",ArtifactManifest.EntryKind.SERVICE,"v1","extension:invoke:example",Set.of("USER"),false)),List.of(),Set.of(),Set.of());
        var bytes=new ByteArrayOutputStream();
        try(var jar=new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry(ArtifactManifest.PATH));jar.write(ProtocolJson.write(manifest));jar.closeEntry();
            var clazz=new org.objectweb.asm.ClassWriter(0);clazz.visit(org.objectweb.asm.Opcodes.V21,org.objectweb.asm.Opcodes.ACC_PUBLIC,"example/Handler",null,"java/lang/Object",null);clazz.visitEnd();
            jar.putNextEntry(new JarEntry("example/Handler.class"));jar.write(clazz.toByteArray());jar.closeEntry();
            for(var entry:extra.entrySet()) {jar.putNextEntry(new JarEntry(entry.getKey()));jar.write(entry.getValue());jar.closeEntry();}
        }
        return ArchiveSnapshot.inspect(bytes.toByteArray(),ResourcePolicy.defaults(),Map.of());
    }
}
