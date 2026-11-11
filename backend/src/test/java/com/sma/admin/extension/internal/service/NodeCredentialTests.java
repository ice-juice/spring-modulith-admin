package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeCredentialTests {
    @TempDir Path dir;
    @Test void rotatingProtectedTokenFileDoesNotRequireRecreatingClientAndMalformedFileFailsClosed()throws Exception {
        Path token=dir.resolve("node.token");Files.writeString(token,"a".repeat(43));
        var properties=new ExtensionProperties(true,URI.create("https://release.example"),"node",null,"production","security","host",dir.resolve("trust.json"),dir.resolve("cache"),List.of("deployment"),token);
        var client=new NodeControlClient(properties);assertThat(client.nodeToken()).isEqualTo("a".repeat(43));
        Path replacement=dir.resolve("replacement.token");Files.writeString(replacement,"b".repeat(43)+"\n");
        Files.move(replacement,token,java.nio.file.StandardCopyOption.REPLACE_EXISTING);assertThat(client.nodeToken()).isEqualTo("b".repeat(43));
        Files.writeString(token,"b".repeat(43)+"\r\nInjected: value");assertThatThrownBy(client::nodeToken).hasMessage("invalid-node-token-file");
        Files.writeString(token,"x".repeat(257));assertThatThrownBy(client::nodeToken).hasMessage("invalid-node-token-file");
    }
}
