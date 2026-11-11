package com.sma.extension.protocol;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Included in the upload action's exact parameters. No build script, compiler option or executable is user selectable. */
public record SourceBuildPlan(ArtifactManifest manifest) {
    public SourceBuildPlan { Checks.require(manifest!=null,"source-manifest-required"); }
    public void validateInputs(List<ActionIntent.FileIntent> files,ResourcePolicy policy,Map<String,String> dependencies) {
        Checks.require(files.stream().anyMatch(f -> f.name().endsWith(".java")),"source-file-required");
        Checks.require(files.size()<=policy.batchFiles(),"invalid-upload-files");
        Set<String> expectedJars=manifest.dependencies().stream().map(d -> d.path().substring("lib/".length())).collect(java.util.stream.Collectors.toSet());
        for(var file:files) {
            ArchiveInspector.validatePath(file.name());
            if(file.name().endsWith(".java")) {
                Checks.require(file.name().matches("[A-Za-z_$][A-Za-z0-9_$]*\\.java") && !file.name().equals("module-info.java"),"invalid-source-filename");
                Checks.require(file.bytes()<=policy.sourceBytes(),"source-quota-exceeded");
            } else Checks.require(expectedJars.contains(file.name()),"undeclared-build-input");
        }
        for(var dependency:manifest.dependencies()) {
            Checks.require(dependency.sha256().equals(dependencies.get(dependency.coordinate())),"dependency-not-allowed");
            Checks.require(files.stream().anyMatch(f -> f.name().equals(dependency.path().substring(4)) && f.sha256().equals(dependency.sha256())),"build-dependency-missing");
        }
    }
    public record Input(String artifactId,String name,String sha256,long bytes) {
        public Input { Checks.id(artifactId);Checks.hash(sha256);Checks.require(bytes>0,"invalid-build-input");ArchiveInspector.validatePath(name);Checks.require(!name.contains("/"),"invalid-build-input"); }
    }
    public record Assignment(SourceBuildPlan plan,List<Input> inputs,String sourceDigest) {
        public Assignment {
            Checks.require(plan!=null,"source-manifest-required");inputs=List.copyOf(inputs);Checks.hash(sourceDigest);
            Checks.unique(inputs.stream().map(Input::artifactId).toList(),20);Checks.unique(inputs.stream().map(Input::name).toList(),20);
            Checks.require(!inputs.isEmpty(),"build-inputs-required");
        }
        public static String digest(SourceBuildPlan plan,List<Input> inputs) {
            return Hashes.sha256(ProtocolJson.write(Map.of("plan",plan,"inputs",inputs.stream().sorted(java.util.Comparator.comparing(Input::name)).toList())));
        }
    }
}
