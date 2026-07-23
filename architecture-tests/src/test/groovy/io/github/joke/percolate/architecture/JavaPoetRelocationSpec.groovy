package io.github.joke.percolate.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

// Change relocate-javapoet-as-spi-api: the atomic cutover (design D5) means no production class in
// spi/processor/strategies-builtin/reactor/reactor-blocking may ever import the unrelocated upstream
// package again - a partial regression back to com.palantir.javapoet would silently reintroduce the
// version-clash risk the relocation exists to eliminate.
@Tag('unit')
class JavaPoetRelocationSpec extends Specification {

    static final String ROOT = 'io.github.joke.percolate'
    static final String UPSTREAM_JAVAPOET = 'com.palantir.javapoet..'

    @Shared
    JavaClasses imported

    def setupSpec() {
        // The relocated io.github.joke.percolate.lib.javapoet package itself is third-party JavaPoet
        // internals (excluded so this rule checks percolate's own code, not the relocated library).
        ImportOption notJavaPoetRelocation = { location -> !location.contains('/io/github/joke/percolate/lib/javapoet/') }
        imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(notJavaPoetRelocation)
                .importPackages(ROOT)
    }

    def 'no production class imports the unrelocated upstream JavaPoet package'() {
        when:
        noClasses().should().dependOnClassesThat().resideInAPackage(UPSTREAM_JAVAPOET)
                .check(imported)

        then:
        notThrown(AssertionError)
    }
}
