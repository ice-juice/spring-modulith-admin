package com.sma.admin.common.request;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RequestCommandTests {
    @Test void capturedInputIsImmutableAndNeverPrinted() {
        byte[] input = "a-secret-value".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var command = new RequestCommand("config.create", "config.values", "resource", "operation-key-0000", input, "config:parameter:create");
        input[0] = 0; byte[] copy = command.input(); copy[0] = 0;
        assertThat(command.input()[0]).isEqualTo((byte)'a');
        assertThat(command.toString()).doesNotContain("a-secret-value", "operation-key-0000", "resource");
        assertThatThrownBy(() -> new RequestCommand("bad\noperation", "config.values", "resource", "operation-key-0000", input, "")).isInstanceOf(IllegalArgumentException.class);
    }
}
