package com.sma.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import com.tngtech.archunit.ArchConfiguration;

/**
 * Verifies the application module structure and renders module documentation to target/spring-modulith-docs.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(SpringbootModulithAdminApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.forEach(System.out::println);
		assertThat(modules.getModuleByName("common")).isPresent();
		boolean hasBusinessModules = StreamSupport.stream(modules.spliterator(), false)
				.anyMatch(module -> module.getBasePackage().getName()
						.startsWith(SpringbootModulithAdminApplication.BUSINESS_PACKAGE + "."));
		if (hasBusinessModules) {
			modules.verify();
			return;
		}
		// Modulith 2.1 evaluates the cycle rule per package root, including empty optional roots.
		ArchConfiguration.withThreadLocalScope(configuration -> {
			configuration.setProperty("archRule.failOnEmptyShould", "false");
			modules.verify();
		});
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml()
				.writeModuleCanvases();
	}

}
