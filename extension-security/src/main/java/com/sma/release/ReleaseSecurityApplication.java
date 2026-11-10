package com.sma.release;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Separate executable and database, never component-scanned into the business host. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReleaseSecurityApplication {
    public static void main(String[] args) { SpringApplication.run(ReleaseSecurityApplication.class, args); }
}
