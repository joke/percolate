package io.github.joke.percolate.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * The genuinely cross-module architecture rules — each needs to see more than one module together
 * (inter-module layering, strategy myopia, acyclicity, the {@code javax.lang.model.util} confinement, the
 * {@code spi.builtins}-spanning size/private-method ceiling) — so this suite is the one place that can
 * enforce them. The "no class outside the engine reaches a processor internal package" rule used to live
 * here too; it moved into each strategy module's own test suite (change
 * decentralize-architecture-boundary-checks), since a strategy module already has everything that rule
 * needs — its own classes plus {@code processor}'s — on its own ordinary classpath, without this module
 * reaching into sibling build output.
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
    // The packages decomposed by change decompose-engine-stages (design D6): every class in them is individually
    // testable, so both structural guards apply here first. The remaining stages (ValidateConstantDefaultLegalityStage,
    // RealisationDiagnosticsStage, ValidateSourceParametersStage, ValidateMappingShapeStage, GraphDumpWriter, and the
    // discover/graph packages) are an explicit audit backlog (openspec/notes.md) — the scope widens as each is
    // decomposed in turn, per the guard's own package-scope-widening plan. Change cutover-strategies-to-mock-seam
    // widened the scope to BUILTINS once the strategies-builtin decomposition (SubtypeDistance extraction, the
    // widen-and-inline passes) landed clean of private methods.
    static final String DECOMPOSED_EXPAND = ROOT + '.processor.internal.stages.expand..'
    static final String DECOMPOSED_GENERATE = ROOT + '.processor.internal.stages.generate..'
    static final String[] DECOMPOSED_ENGINE_PACKAGES = [DECOMPOSED_EXPAND, DECOMPOSED_GENERATE, BUILTINS]
    // Tuned against the decomposed classes: BuildMethodBodies.Walk (13 non-synthetic methods) is the largest
    // legitimate cohesive unit today — a data/query class over shared plan-walk state (design.md's cohesion
    // exception) — so the ceiling clears it with headroom while still catching a regression back toward the
    // pre-decomposition sizes this change eliminated (ExpandStage.Driver was 21 private methods, BuildMethodBodies 17).
    static final int MAX_METHODS_PER_CLASS = 15

    @Shared
    JavaClasses imported

    def setupSpec() {
        // Changes relocate-javapoet-as-spi-api and shade-processor-internal-deps: every shaded
        // third-party dependency (javapoet, jgrapht, jheaps, apfloat, auto-common, guava, dagger)
        // is relocated under io.github.joke.percolate.lib.* purely to avoid a foreign processorpath
        // package clash - it is third-party library internals, not percolate's own code, so the
        // whole `lib` prefix is excluded from every rule below (e.g. auto-common's own visitor
        // classes legitimately extend javax.lang.model.util types).
        ImportOption notShadedLib = { location -> !location.contains('/io/github/joke/percolate/lib/') }
        imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(notShadedLib)
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

    // Rule A (decompose-engine-stages design D6): a private method is statically dispatched (invokespecial) and
    // cannot be intercepted by any test double, so it is not individually testable — the whole reason the engine
    // stages needed decomposing in the first place. Synthetic/bridge members (lambda$.../access$... bridges) are
    // compiler artifacts, not authored methods, so they are exempt; private constructors are automatically exempt
    // (methods() never matches a constructor).
    def 'no method in the decomposed engine packages is private'() {
        given:
        DescribedPredicate<JavaMethod> notSyntheticOrBridge = DescribedPredicate.describe(
                'not a synthetic or bridge method') { JavaMethod method ->
            !method.modifiers.contains(JavaModifier.SYNTHETIC) && !method.modifiers.contains(JavaModifier.BRIDGE)
        }

        when:
        (methods().that().areDeclaredInClassesThat().resideInAnyPackage(DECOMPOSED_ENGINE_PACKAGES)
                & notSyntheticOrBridge)
                .should().notBePrivate()
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    // Rule B (decompose-engine-stages design D6): co-enforced with Rule A — on its own, Rule A is satisfied by
    // exposing a monolith's guts as package-private members, so this ceiling forces separable logic into a new
    // small collaborator instead of a bigger exposed one.
    def 'no class in the decomposed engine packages exceeds the method-count ceiling'() {
        given:
        ArchCondition<JavaClass> sizeCeiling = new ArchCondition<JavaClass>(
                "declare at most $MAX_METHODS_PER_CLASS non-synthetic methods") {
            @Override
            void check(final JavaClass javaClass, final ConditionEvents events) {
                final int count = javaClass.methods.count { !it.modifiers.contains(JavaModifier.SYNTHETIC) }
                final String message =
                        "${javaClass.simpleName} declares $count non-synthetic methods (ceiling $MAX_METHODS_PER_CLASS)"
                events.add(count <= MAX_METHODS_PER_CLASS
                        ? SimpleConditionEvent.satisfied(javaClass, message)
                        : SimpleConditionEvent.violated(javaClass, message))
            }
        }

        when:
        classes().that().resideInAnyPackage(DECOMPOSED_ENGINE_PACKAGES)
                .should(sizeCeiling)
                .check(imported)

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
