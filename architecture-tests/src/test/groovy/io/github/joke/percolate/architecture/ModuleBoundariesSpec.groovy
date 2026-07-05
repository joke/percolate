package io.github.joke.percolate.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import java.nio.file.Paths

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * Production-scope module boundaries, enforced over every percolate module's main classes. The
 * encapsulation rule (no external code reaches a processor `internal` package) and its forced test
 * relocations are added once the engine's internal split exists.
 */
@Tag('unit')
class ModuleBoundariesSpec extends Specification {

    static final String ROOT = 'io.github.joke.percolate'
    static final String ANNOTATIONS = ROOT
    static final String SPI = ROOT + '.spi'
    static final String PROCESSOR = ROOT + '.processor..'
    static final String BUILTINS = ROOT + '.spi.builtins..'
    static final String REACTOR = ROOT + '.reactor..'
    static final String REACTOR_BLOCKING = ROOT + '.reactorblocking..'
    static final String TEST_FOUNDATION = ROOT + '.test..'
    // The engine graph package other-module code must never touch.
    static final String ENGINE_GRAPH = ROOT + '.processor.internal.graph..'
    static final String[] STRATEGY_MODULES = [BUILTINS, REACTOR, REACTOR_BLOCKING]

    @Shared
    JavaClasses imported

    def setupSpec() {
        imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT)
    }

    def 'the engine has no edge to any strategy module'() {
        when:
        noClasses().that().resideInAPackage(PROCESSOR)
                .should().dependOnClassesThat().resideInAnyPackage(STRATEGY_MODULES)
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'the compile harness is strategy-agnostic'() {
        when:
        noClasses().that().resideInAPackage(TEST_FOUNDATION)
                .should().dependOnClassesThat().resideInAnyPackage(STRATEGY_MODULES)
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'the spi contract depends on neither the engine nor any strategy'() {
        when:
        noClasses().that().resideInAPackage(SPI)
                .should().dependOnClassesThat()
                .resideInAnyPackage([PROCESSOR, BUILTINS, REACTOR, REACTOR_BLOCKING] as String[])
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'the annotations depend on no other percolate module'() {
        when:
        noClasses().that().resideInAPackage(ANNOTATIONS)
                .should().dependOnClassesThat()
                .resideInAnyPackage([SPI + '..', PROCESSOR, BUILTINS, REACTOR, REACTOR_BLOCKING, TEST_FOUNDATION] as String[])
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'a strategy implementation may not touch the engine graph'() {
        when:
        noClasses().that().implement(ROOT + '.spi.ExpansionStrategy')
                .should().dependOnClassesThat().resideInAPackage(ENGINE_GRAPH)
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'every Stage implementation is named with a Stage suffix'() {
        when:
        classes().that().implement(ROOT + '.processor.internal.stages.Stage')
                .should().haveSimpleNameEndingWith('Stage')
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    def 'no class outside the engine reaches a processor internal package'() {
        given: 'the strategy modules\' main + test classes, which the normal classpath does not carry'
        final String probe = System.getProperty('percolate.boundaryProbeClasses')
        final List<java.nio.file.Path> dirs = probe.split(java.util.regex.Pattern.quote(File.pathSeparator))
                .findAll { !it.empty }
                .collect { Paths.get(it) }
                .findAll { java.nio.file.Files.isDirectory(it) }
        final JavaClasses candidates = new ClassFileImporter().importPaths(dirs)

        when:
        noClasses().that().resideOutsideOfPackage(ROOT + '.processor..')
                .should().dependOnClassesThat().resideInAPackage(ROOT + '.processor.internal..')
                .check(candidates)

        then:
        notThrown(AssertionError)
    }

    def 'percolate packages are free of cycles'() {
        when:
        slices().matching(ROOT + '.(*)..').should().beFreeOfCycles()
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    // The type-query seam (change type-query-seam): javax.lang.model.util (Types/Elements) — the two
    // compiler-service classes that need a live compile environment to answer — are confined to the seam
    // impl + its DI wiring, the discovery adapter, codegen emission, and the nullability resolver. Every
    // other engine/strategy class asks its type questions through the ResolveCtx seam instead, so TypeMirror
    // stays an opaque pass-through token everywhere else.
    def 'javax.lang.model.util (Types/Elements) is confined to the seam impl, discovery adapter, codegen emission, and the nullability resolver'() {
        given:
        final String PROCESSOR_ROOT = ROOT + '.processor'
        // The bare processor package holds the Dagger wiring (ProcessorModule, MapperStep) plus its
        // generated *_Factory/DaggerProcessorComponent siblings, which necessarily mention Types/Elements too.
        final String[] boundaryPackages = [
                PROCESSOR_ROOT,
                PROCESSOR_ROOT + '.internal.stages.expand',
                PROCESSOR_ROOT + '.internal.stages.discover',
                PROCESSOR_ROOT + '.internal.stages.generate',
                PROCESSOR_ROOT + '.nullability',
        ]
        final List<String> boundaryClasses = [ResolveCtx]*.name
        final DescribedPredicate<JavaClass> notBoundary = DescribedPredicate.describe(
                'not the seam impl, discovery adapter, codegen emission, or nullability resolver') { JavaClass javaClass ->
            !boundaryClasses.contains(javaClass.name)
                    && !boundaryPackages.any { javaClass.packageName == it || javaClass.packageName.startsWith(it + '.') }
        }

        when:
        noClasses().that(notBoundary)
                .should().dependOnClassesThat().resideInAPackage('javax.lang.model.util..')
                .check(imported)

        then:
        notThrown(AssertionError)
    }
}
