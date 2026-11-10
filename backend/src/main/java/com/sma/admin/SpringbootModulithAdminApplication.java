package com.sma.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Springboot Modulith Admin entry point. Each direct sub-package is a Spring Modulith application module;
 * {@code common} is a shared module that every module may depend on.
 */
@Modulithic(systemName = "Springboot Modulith Admin", sharedModules = "common")
@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class SpringbootModulithAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringbootModulithAdminApplication.class, args);
	}

}
