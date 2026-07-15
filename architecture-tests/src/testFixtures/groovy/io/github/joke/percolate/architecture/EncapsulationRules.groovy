package io.github.joke.percolate.architecture

import com.tngtech.archunit.lang.ArchRule

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Reusable ArchUnit rule builders for checks a module runs against its own classpath, so a strategy
 * module verifying it doesn't reach into engine internals doesn't need architecture-tests reaching back
 * into its build output to do so. Carries no dependency on any consuming module — only ArchUnit.
 */
class EncapsulationRules {

    private EncapsulationRules() {
    }

    static ArchRule noClassOutsidePackageDependsOn(String outsidePackage, String forbiddenPackage) {
        noClasses().that().resideOutsideOfPackage(outsidePackage)
                .should().dependOnClassesThat().resideInAPackage(forbiddenPackage)
    }
}
