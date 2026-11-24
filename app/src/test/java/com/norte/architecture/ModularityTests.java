package com.norte.architecture;

import com.norte.NorteApplication;
import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Los módulos de Spring Modulith no tienen ciclos ni usan internos de otros módulos (paso 0.13). */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(NorteApplication.class);

	@Test
	void verifiesModuleStructure() {
		modules.verify();
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
	}

}
