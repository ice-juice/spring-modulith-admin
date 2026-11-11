package com.sma.worker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Production implementation is ContainerRunner; test doubles never enable a native production fallback. */
@FunctionalInterface
public interface IsolatedCommands {
    void run(String image,String entrypoint,List<String> arguments,List<ContainerRunner.Mount> mounts,boolean updateNetwork,Duration timeout,Path log)
            throws IOException,InterruptedException;
    default byte[] runBinary(String image,String entrypoint,List<String> arguments,List<ContainerRunner.Mount> mounts,Duration timeout,Path log,long maximumBytes)
            throws IOException,InterruptedException {
        throw new com.sma.extension.protocol.ProtocolException("bounded-container-output-required");
    }
}
