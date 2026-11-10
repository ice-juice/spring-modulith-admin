package com.sma.admin;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the application module structure and renders module documentation to target/spring-modulith-docs.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(SpringbootModulithAdminApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.forEach(System.out::println);
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml()
				.writeModuleCanvases();
	}

}
