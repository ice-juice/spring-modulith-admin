package com.sma.worker;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.extension.api.BusinessPlugin;
import com.sma.extension.protocol.*;

/** Compiles only the fixed test fixture locally; production always uses the injected container runner. */
class SourceCompilerTests {
    @TempDir Path dir;
    @Test void fixedCompilerPlanProducesReproducibleJarAndRejectsChangedInput() throws Exception {
        Path sdk=sdk();String sdkHash=Hashes.sha256(Files.readAllBytes(sdk));
        var settings=new WorkerSettings(URI.create("https://security.example"),"x".repeat(43),"production",dir,dir.resolve("docker"),
                "scanner@sha256:"+"a".repeat(64),"java@sha256:"+"b".repeat(64),dir.resolve("db"),"65532:65532");
        IsolatedCommands fixtureRunner=new IsolatedCommands() {
            public void run(String image,String entrypoint,List<String> args,List<ContainerRunner.Mount> mounts,boolean network,java.time.Duration timeout,Path log){throw new AssertionError("Binary container execution required");}
            public byte[] runBinary(String image,String entrypoint,List<String> args,List<ContainerRunner.Mount> mounts,java.time.Duration timeout,Path log,long maximum)throws java.io.IOException {
            assertThat(entrypoint).isEqualTo("java");assertThat(args).contains("-proc:none","--release","21","-Xmx512m");assertThat(mounts).allMatch(ContainerRunner.Mount::readOnly);
            Path classes=Files.createDirectory(log.getParent().resolve("fixture-classes"));
            int options=args.indexOf("--release");
            var local=args.subList(options,args.size()).stream().map(a -> {
                String value=a;
                if(value.equals("/build/classes"))return classes.toString();
                for(var mount:mounts) value=value.replace(mount.destination()+"/",mount.source().toString()+java.io.File.separator);
                return value;
            }).toArray(String[]::new);
            assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,local)).isZero();
            var output=new java.io.ByteArrayOutputStream();com.sma.worker.build.CompilerDriver.export(classes,output,ResourcePolicy.defaults().expandedBytes(),maximum,ResourcePolicy.defaults().entries());return output.toByteArray();
            }
        };
        var compiler=new SourceCompiler(settings,fixtureRunner,sdk,sdkHash);
        byte[] source="package example; import com.sma.extension.api.*; public class Example implements BusinessPlugin { public PluginResult execute(PluginCall c) { return new PluginResult(c.input()); } }".getBytes(StandardCharsets.UTF_8);
        var manifest=new ArtifactManifest(1,"example","1.0.0",1,"example.Example",List.of(new ArtifactManifest.Entry("execute",ArtifactManifest.EntryKind.SERVICE,"v1","extension:invoke:example",Set.of("USER"),false)),List.of(),Set.of(),Set.of());
        var plan=new SourceBuildPlan(manifest);var inputs=List.of(new SourceBuildPlan.Input("source-one","Example.java",Hashes.sha256(source),source.length));
        var build=new SourceBuildPlan.Assignment(plan,inputs,SourceBuildPlan.Assignment.digest(plan,inputs));
        var first=compiler.compile(build,Map.of("source-one",source),Map.of(),ResourcePolicy.defaults(),Files.createDirectory(dir.resolve("first")));
        var second=compiler.compile(build,Map.of("source-one",source),Map.of(),ResourcePolicy.defaults(),Files.createDirectory(dir.resolve("second")));
        assertThat(first.sha256()).isEqualTo(second.sha256()).isNotEqualTo(Hashes.sha256(source));
        assertThat(first.sourceDigest()).isEqualTo(build.sourceDigest());
        assertThat(ArchiveSnapshot.inspect(first.jar(),ResourcePolicy.defaults(),Map.of()).manifest()).isEqualTo(manifest);
        byte[] changed=source.clone();changed[0]^=1;
        assertThatThrownBy(() -> compiler.compile(build,Map.of("source-one",changed),Map.of(),ResourcePolicy.defaults(),Files.createDirectory(dir.resolve("changed")))).hasMessage("build-input-hash-mismatch");
    }
    private Path sdk()throws Exception {
        Path classes=Path.of(BusinessPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());Path jar=dir.resolve("sdk.jar");
        if(Files.isRegularFile(classes)) return Files.copy(classes,jar);
        try(var out=new JarOutputStream(Files.newOutputStream(jar));var paths=Files.walk(classes)) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) {
                String relative=classes.relativize(path).toString().replace('\\','/');if(!relative.endsWith(".class"))continue;
                out.putNextEntry(new JarEntry(relative));out.write(Files.readAllBytes(path));out.closeEntry();
            }
        }return jar;
    }
}
