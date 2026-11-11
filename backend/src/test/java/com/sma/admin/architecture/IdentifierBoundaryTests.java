package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

class IdentifierBoundaryTests {
    @Test void businessAndPlatformModulesCannotInstallIndependentFallbackGenerators(){
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator").check(ArchitectureRulesTests.CLASSES);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("com.baomidou.mybatisplus.core.toolkit.Sequence").check(ArchitectureRulesTests.CLASSES);
        classes().that().areAssignableTo(IdentifierGenerator.class).should().resideInAPackage("com.sma.admin.common.internal.persistence..").check(ArchitectureRulesTests.CLASSES);
        for(var type:ArchitectureRulesTests.CLASSES)for(var call:type.getMethodCallsFromSelf()){
            if(call.getTargetOwner().getName().equals("com.baomidou.mybatisplus.core.toolkit.IdWorker")
                    && java.util.Set.of("initSequence","setIdentifierGenerator").contains(call.getTarget().getName()))
                assertThat(type.getName()).isEqualTo("com.sma.admin.common.internal.IdentifierConfiguration");
        }
    }
}
