package com.sma.release;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.extension.protocol.Checks;

@ConfigurationProperties("release")
public record ReleaseProperties(String issuer, String application, Path directory, String encryptionKey, String origin,
        String bootstrapFile) {
    public ReleaseProperties {
        Checks.id(issuer); Checks.id(application);
        Checks.require(directory != null && encryptionKey != null && !encryptionKey.isBlank(), "release-key-and-directory-required");
        Checks.require(origin != null && origin.startsWith("https://"), "release-https-origin-required");
    }
    @Override public String toString() { return "ReleaseProperties[issuer="+issuer+", secrets=redacted]"; }
}
