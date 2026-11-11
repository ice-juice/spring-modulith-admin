package com.sma.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import com.sma.extension.protocol.*;

/** Fixed javac pipeline: no Maven/Gradle script, annotation processor, requested compiler flags or runtime network. */
public final class SourceCompiler {
    private final WorkerSettings settings;private final IsolatedCommands runner;private final byte[] sdk;private final String sdkHash;
    public SourceCompiler(WorkerSettings settings,IsolatedCommands runner,Path sdkJar,String expectedHash) throws IOException {
        this.settings=settings;this.runner=runner;Checks.hash(expectedHash);
        Checks.require(Files.isRegularFile(sdkJar,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(sdkJar) && Files.size(sdkJar)<=10*ResourcePolicy.MIB,"sdk-binary-required");
        sdk=Files.readAllBytes(sdkJar);sdkHash=Hashes.sha256(sdk);Checks.require(sdkHash.equals(expectedHash),"sdk-binary-hash-mismatch");
    }
    public record Result(byte[] jar,String sha256,String sourceDigest,String toolchainDigest) {
        public Result {jar=jar.clone();}
        @Override public byte[] jar(){return jar.clone();}
    }
    public Result compile(SourceBuildPlan.Assignment build,Map<String,byte[]> inputs,Map<String,String> dependencies,ResourcePolicy policy,Path job) throws IOException,InterruptedException {
        Checks.require(build.sourceDigest().equals(SourceBuildPlan.Assignment.digest(build.plan(),build.inputs())),"source-assignment-mismatch");
        build.plan().validateInputs(build.inputs().stream().map(i -> new ActionIntent.FileIntent(i.name(),i.sha256(),i.bytes())).toList(),policy,dependencies);
        Checks.require(inputs.keySet().equals(build.inputs().stream().map(SourceBuildPlan.Input::artifactId).collect(java.util.stream.Collectors.toSet())),"build-input-set-mismatch");
        Path input=Files.createDirectory(job.resolve("compile-input")),sdkDir=Files.createDirectory(job.resolve("compile-sdk"));
        Files.write(sdkDir.resolve("sdk.jar"),sdk);
        byte[] driver=compilerDriver();Files.write(sdkDir.resolve("compiler.jar"),driver);
        for(var file:build.inputs()) {
            byte[] bytes=inputs.get(file.artifactId());
            Checks.require(bytes!=null && bytes.length==file.bytes() && Hashes.sha256(bytes).equals(file.sha256()),"build-input-hash-mismatch");
            Files.write(input.resolve(file.name()),bytes,java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        String classpath="/sdk/sdk.jar";
        for(var dependency:build.plan().manifest().dependencies()) classpath+=":/input/"+dependency.path().substring(4);
        var args=new ArrayList<>(List.of("-Xmx512m","-XX:+DisableAttachMechanism","-cp","/sdk/compiler.jar","com.sma.worker.build.CompilerDriver",
                Long.toString(policy.expandedBytes()),Long.toString(policy.jarBytes()),Integer.toString(policy.entries()),
                "--release","21","-proc:none","-encoding","UTF-8","-classpath",classpath,"-d","/build/classes"));
        build.inputs().stream().filter(i -> i.name().endsWith(".java")).sorted(java.util.Comparator.comparing(SourceBuildPlan.Input::name)).forEach(i -> args.add("/input/"+i.name()));
        byte[] classes=runner.runBinary(settings.javaImage(),"java",args,List.of(new ContainerRunner.Mount(input,"/input",true),new ContainerRunner.Mount(sdkDir,"/sdk",true)),
                Duration.ofSeconds(policy.workerSeconds()),job.resolve("compiler.log"),policy.jarBytes());
        byte[] jar=packageOutput(classes,build.plan().manifest(),build.inputs(),inputs,policy);
        ArchiveSnapshot.inspect(jar,policy,dependencies);
        String toolchain=Hashes.sha256(ProtocolJson.write(Map.of("pipeline",2,"javaImage",settings.javaImage(),"sdkSha256",sdkHash,"driverSha256",Hashes.sha256(driver),"compilerOptions",args)));
        return new Result(jar,Hashes.sha256(jar),build.sourceDigest(),toolchain);
    }
    private byte[] packageOutput(byte[] classes,ArtifactManifest manifest,List<SourceBuildPlan.Input> sources,Map<String,byte[]> inputs,ResourcePolicy policy) throws IOException {
        Checks.require(classes.length<=policy.jarBytes(),"compiler-output-limit");
        var result=new ByteArrayOutputStream();long expanded=0;int count=1;
        try(var jar=new JarOutputStream(new java.io.FilterOutputStream(result) {
            long bytes;
            @Override public void write(int value)throws IOException{Checks.require(++bytes<=policy.jarBytes(),"build-output-too-large");out.write(value);}
            @Override public void write(byte[] values,int offset,int length)throws IOException{Checks.require(length<=policy.jarBytes()-bytes,"build-output-too-large");bytes+=length;out.write(values,offset,length);}
        })) {
            writeEntry(jar,ArtifactManifest.PATH,ProtocolJson.write(manifest));
            var names=new java.util.HashSet<String>();
            try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(classes))) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    String name=entry.getName();ArchiveInspector.validatePath(name);
                    Checks.require(!entry.isDirectory() && name.endsWith(".class") && names.add(name),"unexpected-compiler-output");
                    Checks.require(++count<=policy.entries(),"compiler-output-limit");
                    var data=new ByteArrayOutputStream();byte[] block=new byte[8192];int read;
                    while((read=zip.read(block))!=-1){Checks.require(read<=policy.expandedBytes()-expanded,"compiler-output-limit");expanded+=read;data.write(block,0,read);}
                    writeEntry(jar,name,data.toByteArray());zip.closeEntry();
                }
            }
            Checks.require(!names.isEmpty(),"compiler-output-missing");
            for(var dependency:manifest.dependencies()) {
                var source=sources.stream().filter(i -> i.name().equals(dependency.path().substring(4))).findFirst().orElseThrow();
                byte[] bytes=inputs.get(source.artifactId());Checks.require(bytes.length<=policy.expandedBytes()-expanded,"compiler-output-limit");expanded+=bytes.length;
                writeEntry(jar,dependency.path(),bytes);
            }
        }
        return result.toByteArray();
    }
    private static byte[] compilerDriver()throws IOException {
        String name="com/sma/worker/build/CompilerDriver.class";var bytes=new ByteArrayOutputStream();
        try(var input=SourceCompiler.class.getClassLoader().getResourceAsStream(name);var jar=new JarOutputStream(bytes)) {
            Checks.require(input!=null,"compiler-driver-missing");writeEntry(jar,name,input.readAllBytes());
        }return bytes.toByteArray();
    }
    private static void writeEntry(JarOutputStream output,String name,byte[] bytes)throws IOException {
        var entry=new JarEntry(name);entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));output.putNextEntry(entry);output.write(bytes);output.closeEntry();
    }
}
