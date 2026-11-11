package com.sma.extension.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;

/** Only launched inside the isolated worker container. Never called by the host's admission path. */
public final class ContractProbe {
    private ContractProbe() {}
    @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
        Checks.require(args.length==2,"probe-input-required");
        Path artifact=Path.of(args[0]),allowlist=Path.of(args[1]);
        Checks.require(Files.size(artifact)<=ResourcePolicy.defaults().jarBytes() && Files.size(allowlist)<=65536,"probe-input-too-large");
        Map<String,String> allowed=ProtocolJson.read(Files.readAllBytes(allowlist),Map.class);
        var snapshot=ArchiveSnapshot.inspect(Files.readAllBytes(artifact),ResourcePolicy.defaults(),allowed);
        var loader=new SnapshotClassLoader(snapshot);
        var plugin=loader.loadClass(snapshot.manifest().implementationClass()).asSubclass(BusinessPlugin.class).getConstructor().newInstance();
        try {
            plugin.initialize(new PluginContext(snapshot.manifest().pluginId(),snapshot.manifest().version(),(id,input) -> {
                throw new ProtocolException("initialization-side-effect-forbidden");
            },(metric,amount) -> Checks.require(snapshot.manifest().metrics().contains(metric) && amount>=0,"invalid-probe-metric")));
        } finally {plugin.close();}
    }
}
