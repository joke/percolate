package io.github.joke.percolate.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

/**
 * Runs the shared "no class reaches processor internals" rule (architecture-tests' testFixtures) against
 * this module's own classpath — main and test classes alike, both already present here via the ordinary
 * {@code testImplementation project(':processor')} dependency, so no cross-project probing is needed.
 */
@Tag('unit')
class EngineEncapsulationSpec extends Specification {

    static final String ROOT = 'io.github.joke.percolate'
    static final String PROCESSOR = ROOT + '.processor..'
    static final String PROCESSOR_INTERNAL = ROOT + '.processor.internal..'

    @Shared
    JavaClasses imported

    def setupSpec() {
        imported = new ClassFileImporter().importPackages(ROOT)
    }

    def 'no class in this module reaches a processor internal package'() {
        when:
        EncapsulationRules.noClassOutsidePackageDependsOn(PROCESSOR, PROCESSOR_INTERNAL).check(imported)

        then:
        notThrown(AssertionError)
    }
}
