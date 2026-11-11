package com.sma.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Platform and business modules share one application. Each direct child of
 * either package root is a module; {@code common} remains shared.
 */
@Modulithic(systemName = "Springboot Modulith Admin", sharedModules = "common",
        additionalPackages = SpringbootModulithAdminApplication.BUSINESS_PACKAGE)
@SpringBootApplication(
        scanBasePackages = {SpringbootModulithAdminApplication.PLATFORM_PACKAGE, SpringbootModulithAdminApplication.BUSINESS_PACKAGE},
        exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class,
        nameGenerator = org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
public class SpringbootModulithAdminApplication {

    public static final String PLATFORM_PACKAGE = "com.sma.admin";
    public static final String BUSINESS_PACKAGE = "com.sma.business";

	public static void main(String[] args) {
		SpringApplication.run(SpringbootModulithAdminApplication.class, args);
	}

}
