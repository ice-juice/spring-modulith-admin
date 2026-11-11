package com.sma.admin.mail.internal.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.mail.work")
public record MailWorkProperties(int maximumQueuedPerProfile, int maximumAttempts, Duration leaseDuration) {
    public MailWorkProperties {
        maximumQueuedPerProfile = maximumQueuedPerProfile == 0 ? 10000 : maximumQueuedPerProfile;
        maximumAttempts = maximumAttempts == 0 ? 5 : maximumAttempts;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(2) : leaseDuration;
        if (maximumQueuedPerProfile < 1 || maximumQueuedPerProfile > 100000 || maximumAttempts < 1 || maximumAttempts > 10
                || leaseDuration.compareTo(Duration.ofSeconds(30)) < 0 || leaseDuration.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("Invalid mail work bounds");
    }
}
