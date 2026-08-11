package io.github.joke.percolate.architecture;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.joke.percolate.architecture.Packages.ENGINE_GRAPH;
import static io.github.joke.percolate.architecture.Packages.PROCESSOR;
import static io.github.joke.percolate.architecture.Packages.PROCESSOR_INTERNAL;
import static io.github.joke.percolate.architecture.Packages.ROOT;
import static java.util.stream.Collectors.joining;

/** Rules keeping the engine's internals, and the engine's ignorance of user-facing annotations, intact. */
public class EngineEncapsulationRules implements ArchRulesService {

    /**
     * D7 of change decouple-engine-from-strategy-semantics: the processor reads no user-facing mapping
     * annotation — a DirectiveReader translates it instead. {@code @Mapper} stays core (MapperStep decides
     * WHAT to generate), so it is the one exempt annotation. The names are matched exactly, not by prefix,
     * so the rule cannot silently pass by matching nothing.
     */
    static final List<String> MAPPING_ANNOTATIONS =
            List.of(ROOT + ".Map", ROOT + ".MapList", ROOT + ".MapEnum", ROOT + ".MapEnumList", ROOT + ".Ambient");

    /** D13: the nullability resolver legitimately reads annotations — it is not part of the engine. */
    static final String NULLABILITY_PACKAGE = ROOT + ".processor.nullability";

    static final String ELEMENT = "javax.lang.model.element.Element";

    /** The two {@code Element} methods that hand back a raw annotation. */
    static final Set<String> ANNOTATION_READERS = Set.of("getAnnotationMirrors", "getAnnotation");

    /** Lambda/{@code access$} bridges and Groovy's synthetic accessors are compiler artifacts. */
    static final DescribedPredicate<JavaMethod> NOT_SYNTHETIC_OR_BRIDGE = describe(
            "not a synthetic or bridge method",
            method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                    && !method.getModifiers().contains(JavaModifier.BRIDGE));

    static final ArchRule PROCESSOR_READS_NO_MAPPING_ANNOTATION = noClasses()
            .that()
            .resideInAPackage(PROCESSOR)
            .should()
            .dependOnClassesThat()
            .haveNameMatching(MAPPING_ANNOTATIONS.stream().map(Pattern::quote).collect(joining("|")))
            .allowEmptyShould(true)
            .as("No processor class depends on a user-facing mapping annotation")
            .because("user-facing mapping annotations are read at the DirectiveReader boundary and never "
                    + "inside the processor — only @Mapper, which decides what to generate, stays core");

    /**
     * D7/D13: annotation reading is confined to the readers (SPI-side) and the nullability resolver. Every
     * other engine class asks the SPI's own opaque surfaces instead.
     *
     * <p>The predicate tests the call target's owner with {@code isAssignableTo(Element)}, which resolves the
     * non-imported javax hierarchy off the classpath. Note that a receiver typed as {@code AnnotatedConstruct}
     * — a <em>supertype</em> of {@code Element} — is not matched; the fixture in {@code archRulesTest} uses an
     * {@code Element}-typed receiver so the test exercises what the rule actually tests.
     */
    static final ArchRule ENGINE_READS_NO_RAW_ANNOTATION = methods()
            .that(NOT_SYNTHETIC_OR_BRIDGE)
            .and()
            .areDeclaredInClassesThat(describe(
                    "not the nullability resolver",
                    (JavaClass javaClass) -> !NULLABILITY_PACKAGE.equals(javaClass.getPackageName())))
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage(PROCESSOR)
            .should(new ReadsRawAnnotation())
            .allowEmptyShould(true)
            .as("No engine class reads a raw annotation off an Element")
            .because("the engine interprets no annotation — reading one belongs to the DirectiveReaders and "
                    + "to the single nullability resolver, both outside the engine");

    static final ArchRule STRATEGY_MAY_NOT_TOUCH_THE_GRAPH = noClasses()
            .that()
            .implement(ROOT + ".spi.ExpansionStrategy")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ENGINE_GRAPH)
            .allowEmptyShould(true)
            .as("A strategy implementation may not touch the engine graph")
            .because("a strategy decides locally from its Demand and ResolveCtx; the driver owns the graph");

    /**
     * Formerly three near-identical {@code EngineEncapsulationSpec} copies in strategies-builtin, reactor,
     * and reactor-blocking, each wrapping a shared {@code testFixtures} rule builder. One rule now, running
     * in every module that applies the runner — strictly broader coverage than the three that had a copy.
     */
    static final ArchRule ENGINE_INTERNALS_ARE_ENCAPSULATED = noClasses()
            .that()
            .resideOutsideOfPackage(PROCESSOR)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PROCESSOR_INTERNAL)
            .allowEmptyShould(true)
            .as("No class outside the engine reaches a processor internal package")
            .because("other modules reach the engine only through its public surface and through spi");

    /**
     * Named rather than anonymous so the condition is itself an addressable, individually testable
     * declaration — the same reason no method in percolate is private.
     */
    static final class ReadsRawAnnotation extends ArchCondition<JavaMethod> {

        ReadsRawAnnotation() {
            super("call Element#getAnnotationMirrors() or Element#getAnnotation(Class)");
        }

        @Override
        public void check(final JavaMethod method, final ConditionEvents events) {
            final var calls = method.getCallsFromSelf().stream()
                    .anyMatch(
                            call -> ANNOTATION_READERS.contains(call.getTarget().getName())
                                    && call.getTarget().getOwner().isAssignableTo(ELEMENT));
            final var message = method.getFullName() + (calls ? " reads" : " does not read") + " a raw annotation";
            events.add(
                    calls
                            ? SimpleConditionEvent.violated(method, message)
                            : SimpleConditionEvent.satisfied(method, message));
        }
    }

    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of(
                "processor-reads-no-mapping-annotation", PROCESSOR_READS_NO_MAPPING_ANNOTATION,
                "engine-reads-no-raw-annotation", ENGINE_READS_NO_RAW_ANNOTATION,
                "strategy-may-not-touch-graph", STRATEGY_MAY_NOT_TOUCH_THE_GRAPH,
                "engine-internals-encapsulated", ENGINE_INTERNALS_ARE_ENCAPSULATED);
    }
}
