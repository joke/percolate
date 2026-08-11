package io.github.joke.percolate.architecture;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.joke.percolate.architecture.Packages.ANNOTATIONS;
import static io.github.joke.percolate.architecture.Packages.BUILTINS;
import static io.github.joke.percolate.architecture.Packages.PROCESSOR;
import static io.github.joke.percolate.architecture.Packages.REACTOR;
import static io.github.joke.percolate.architecture.Packages.REACTOR_BLOCKING;
import static io.github.joke.percolate.architecture.Packages.SPI;
import static io.github.joke.percolate.architecture.Packages.SPI_TREE;
import static io.github.joke.percolate.architecture.Packages.STRATEGY_MODULES;
import static io.github.joke.percolate.architecture.Packages.TEST_FOUNDATION;

/**
 * The declared module layering. Every rule here is an <em>outgoing dependency</em> rule, so it only needs
 * the source module imported — evaluating it inside the owning module is strictly more precise than the
 * union-classpath import this library replaced.
 */
public class ModuleLayeringRules implements ArchRulesService {

    static final ArchRule ENGINE_HAS_NO_EDGE_TO_STRATEGY = noClasses()
            .that()
            .resideInAPackage(PROCESSOR)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(STRATEGY_MODULES)
            .allowEmptyShould(true)
            .as("The engine has no edge to any strategy module")
            .because("the engine must stay strategy-agnostic; strategies plug in through the spi contract");

    static final ArchRule HARNESS_IS_STRATEGY_AGNOSTIC = noClasses()
            .that()
            .resideInAPackage(TEST_FOUNDATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(STRATEGY_MODULES)
            .allowEmptyShould(true)
            .as("The compile harness is strategy-agnostic")
            .because("test-foundation drives the engine with a FakeStrategy and must not bind to a real one");

    static final ArchRule SPI_DEPENDS_ON_NEITHER_SIDE = noClasses()
            .that()
            .resideInAPackage(SPI)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PROCESSOR, BUILTINS, REACTOR, REACTOR_BLOCKING)
            .allowEmptyShould(true)
            .as("The spi contract depends on neither the engine nor any strategy")
            .because("spi is the contract both sides implement, so it may depend on neither side");

    static final ArchRule ANNOTATIONS_DEPEND_ON_NOTHING = noClasses()
            .that()
            .resideInAPackage(ANNOTATIONS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SPI_TREE, PROCESSOR, BUILTINS, REACTOR, REACTOR_BLOCKING, TEST_FOUNDATION)
            .allowEmptyShould(true)
            .as("The annotations depend on no other percolate module")
            .because("a consumer puts annotations on its compile classpath without pulling in the processor");

    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of(
                "engine-no-edge-to-strategy", ENGINE_HAS_NO_EDGE_TO_STRATEGY,
                "harness-strategy-agnostic", HARNESS_IS_STRATEGY_AGNOSTIC,
                "spi-depends-on-neither-side", SPI_DEPENDS_ON_NEITHER_SIDE,
                "annotations-depend-on-nothing", ANNOTATIONS_DEPEND_ON_NOTHING);
    }
}
