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
    // testable, so the size-ceiling guard (Rule B below) applies here. The no-private guard (Rule A) itself widened
    // to every percolate package repo-wide in change enforce-testable-method-visibility, once the remaining stages
    // (validate/*, graph, dump) and the other flagged classes (spi.LiteralCoercion, MapperStep, ProcessorOptions,
    // GoalSpec, JspecifyNullabilityResolver, spi.Nullability/ResolveCtx, reactorblocking.Blockings) were decomposed
    // clean of private methods too — Rule B stays scoped here, not widened with it (see that change's design.md for
    // why the size ceiling is a deliberate non-goal rather than an oversight).
    static final String DECOMPOSED_EXPAND = ROOT + '.processor.internal.stages.expand..'
    static final String DECOMPOSED_GENERATE = ROOT + '.processor.internal.stages.generate..'
    static final String[] DECOMPOSED_ENGINE_PACKAGES = [DECOMPOSED_EXPAND, DECOMPOSED_GENERATE, BUILTINS]
    // Tuned against the decomposed classes: BuildMethodBodies.Walk (13 non-synthetic methods) is the largest
    // legitimate cohesive unit today — a data/query class over shared plan-walk state (design.md's cohesion
    // exception) — so the ceiling clears it with headroom while still catching a regression back toward the
    // pre-decomposition sizes this change eliminated (ExpandStage.Driver was 21 private methods, BuildMethodBodies 17).
    static final int MAX_METHODS_PER_CLASS = 15

    // Shared by Rule A and Rule C: lambda$.../access$... bridges and Groovy's synthetic accessors
    // (e.g. $getStaticMetaClass) are compiler artifacts, not authored methods.
    static final DescribedPredicate<JavaMethod> NOT_SYNTHETIC_OR_BRIDGE = DescribedPredicate.describe(
            'not a synthetic or bridge method') { JavaMethod method ->
        !method.modifiers.contains(JavaModifier.SYNTHETIC) && !method.modifiers.contains(JavaModifier.BRIDGE)
    }

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

    // D7 of change decouple-engine-from-strategy-semantics: the processor module reads no user-facing mapping
    // annotation — a DirectiveReader translates it instead. @Mapper stays core (MapperStep decides WHAT to
    // generate), so it is the one exempt annotation. ANNOTATIONS has no trailing '..' (it names the exact
    // package, not a subtree) because every one of these annotations lives directly in it, alongside unrelated
    // root-level classes (e.g. Mapper itself) that must not be swept in by a wildcard.
    def 'no processor class depends on a user-facing mapping annotation'() {
        given:
        final List<String> mappingAnnotations = [
                ROOT + '.Map',
                ROOT + '.MapList',
                ROOT + '.MapEnum',
                ROOT + '.MapEnumList',
                ROOT + '.Ambient',
        ]

        when:
        noClasses().that().resideInAPackage(PROCESSOR)
                .should().dependOnClassesThat().haveNameMatching(
                        mappingAnnotations.collect { java.util.regex.Pattern.quote(it) }.join('|'))
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    // D7/D13: annotation reading is confined to the readers (SPI-side, outside this scope) and the nullability
    // resolver (D13: not a leak — it resolves nullness for elements no scope input declares, e.g. a method-call
    // return type). Every other engine class asks the SPI's own opaque surfaces instead.
    def 'no engine class reads a raw annotation off an Element'() {
        given:
        final DescribedPredicate<JavaClass> notNullabilityResolver = DescribedPredicate.describe(
                'not the nullability resolver') { JavaClass javaClass ->
            javaClass.packageName != ROOT + '.processor.nullability'
        }
        ArchCondition<JavaMethod> callsRawAnnotationRead = new ArchCondition<JavaMethod>(
                'call Element#getAnnotationMirrors() or Element#getAnnotation(Class)') {
            @Override
            void check(final JavaMethod method, final ConditionEvents events) {
                final boolean calls = method.callsFromSelf.any { call ->
                    final String name = call.target.name
                    (name == 'getAnnotationMirrors' || name == 'getAnnotation')
                            && call.target.owner.isAssignableTo('javax.lang.model.element.Element')
                }
                final String message = "${method.fullName} ${calls ? 'reads' : 'does not read'} a raw annotation"
                events.add(calls
                        ? SimpleConditionEvent.violated(method, message)
                        : SimpleConditionEvent.satisfied(method, message))
            }
        }

        when:
        (methods() & NOT_SYNTHETIC_OR_BRIDGE)
                .that().areDeclaredInClassesThat(notNullabilityResolver)
                .and().areDeclaredInClassesThat().resideInAPackage(PROCESSOR)
                .should(callsRawAnnotationRead)
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

    // Rule A (decompose-engine-stages design D6, widened repo-wide by enforce-testable-method-visibility): a private
    // method is statically dispatched (invokespecial) and cannot be intercepted by any test double, so it is not
    // individually testable. Synthetic/bridge members (lambda$.../access$... bridges) are compiler artifacts, not
    // authored methods, so they are exempt; private constructors are automatically exempt (methods() never matches
    // a constructor). Classes carrying @Generated (Dagger's DaggerProcessorComponent et al.) are compiler output too
    // — repo-wide scope newly reaches the bare `processor` package where Dagger generates its component, which the
    // narrower engine-package scope never did. No package filter is needed otherwise: `imported` already excludes
    // `io.github.joke.percolate.lib..` (shaded third-party sources, see setupSpec) and test sources, so every
    // remaining authored method is percolate's own.
    def 'no method anywhere in percolate is private'() {
        given:
        DescribedPredicate<JavaMethod> notGenerated = DescribedPredicate.describe(
                'not declared in a @Generated class') { JavaMethod method ->
            !isGeneratedOrNestedInGenerated(method.owner)
        }

        when:
        (methods() & NOT_SYNTHETIC_OR_BRIDGE & notGenerated)
                .should().notBePrivate()
                .check(imported)

        then:
        notThrown(AssertionError)
    }

    // Rule C (enforce-testable-method-visibility design D3/D4): protected is sometimes used purely to dodge Rule A's
    // private ban, without being a real inheritance extension point. A concrete protected method is a genuine
    // extension point when a production-code subclass (DO_NOT_INCLUDE_TESTS already excludes test-only subclassing,
    // same as Rule A) either overrides it — a method of the same name/raw-parameter-types declared directly on the
    // subclass, per JavaClass.getMethods() returning only directly-declared members, not inherited ones — or calls
    // it via an inherited (non-overriding) invocation. Anything else must carry @VisibleForTesting to document
    // "test-only widening, not a real extension point" as a build-checked fact instead of tribal knowledge. Abstract
    // protected methods are exempt outright (D4): they have no body to test, and every concrete subclass overrides
    // them by construction. Lombok-generated protected methods (e.g. @EqualsAndHashCode's canEqual on a final
    // @Value leaf, which is never subclassed) are exempt too: lombok.config sets addLombokGeneratedAnnotation, so
    // they carry lombok.Generated in bytecode, and there is no source declaration to hang @VisibleForTesting on —
    // the same "generated code isn't an authored method" principle Rule A already applies to Dagger's output.
    def 'unused protected methods are marked with VisibleForTesting'() {
        given:
        DescribedPredicate<JavaMethod> concreteProtected = DescribedPredicate.describe(
                'a concrete protected method') { JavaMethod method ->
            method.modifiers.contains(JavaModifier.PROTECTED) && !method.modifiers.contains(JavaModifier.ABSTRACT)
        }
        DescribedPredicate<JavaMethod> notLombokGenerated = DescribedPredicate.describe(
                'not a Lombok-generated method') { JavaMethod method ->
            !method.isAnnotatedWith('lombok.Generated')
        }
        ArchCondition<JavaMethod> usedBySubclassOrAnnotated = new ArchCondition<JavaMethod>(
                'be overridden or called by a subclass, or carry @VisibleForTesting') {
            @Override
            void check(final JavaMethod method, final ConditionEvents events) {
                final Set<JavaClass> subclasses = method.owner.allSubclasses
                final boolean overridden = subclasses.any { subclass ->
                    subclass.methods.any {
                        it.name == method.name && it.rawParameterTypes == method.rawParameterTypes
                    }
                }
                final boolean calledBySubclass = method.callsOfSelf.any { it.originOwner in subclasses }
                final boolean annotated = method.isAnnotatedWith('org.jetbrains.annotations.VisibleForTesting')
                final boolean satisfied = overridden || calledBySubclass || annotated
                final String message = satisfied
                        ? "${method.fullName} is a genuine extension point or annotated @VisibleForTesting"
                        : "${method.fullName} has no subclass usage and no @VisibleForTesting annotation"
                events.add(satisfied
                        ? SimpleConditionEvent.satisfied(method, message)
                        : SimpleConditionEvent.violated(method, message))
            }
        }

        when:
        (methods() & NOT_SYNTHETIC_OR_BRIDGE & notLombokGenerated & concreteProtected)
                .should(usedBySubclassOrAnnotated)
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

    // javax.annotation.processing.Generated is SOURCE-retention (stripped before bytecode), so ArchUnit's
    // bytecode-based import never sees it on Dagger's DaggerXxxComponent classes despite it being in the generated
    // source — dagger.internal.DaggerGenerated (CLASS-retention) is what actually survives to the .class file and
    // is checked here instead. relocate-javapoet-as-spi-api's shading relocates dagger under
    // io.github.joke.percolate.lib.dagger, so the annotation's runtime name is ROOT + '.lib.dagger...', not the
    // upstream 'dagger.internal.DaggerGenerated'. It lands on the top-level DaggerXxxComponent class, not on the
    // nested *Impl class whose methods actually trip Rule A (e.g. DaggerProcessorComponent$ProcessorComponentImpl),
    // so the check walks up the enclosing-class chain rather than checking the method's immediate owner only.
    private static boolean isGeneratedOrNestedInGenerated(final JavaClass clazz) {
        clazz.isAnnotatedWith(ROOT + '.lib.dagger.internal.DaggerGenerated')
                || clazz.enclosingClass.map { isGeneratedOrNestedInGenerated(it) }.orElse(false)
    }
}
