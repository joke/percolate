package io.github.joke.percolate.architecture;

/**
 * The package coordinates every rule in this library is expressed against.
 *
 * <p>Rules are evaluated per source set inside each consuming module, so a rule scoped to one module simply
 * matches nothing in the others. That is why every rule sets {@code allowEmptyShould(true)} — and why every
 * rule also carries a negative fixture in {@code archRulesTest}, since an empty match is otherwise
 * indistinguishable from a mistyped coordinate.
 */
final class Packages {

    static final String ROOT = "io.github.joke.percolate";

    /** The annotations live directly in the root package, alongside unrelated root-level classes. */
    static final String ANNOTATIONS = ROOT;

    static final String SPI = ROOT + ".spi";
    static final String SPI_TREE = SPI + "..";
    static final String PROCESSOR = ROOT + ".processor..";
    static final String PROCESSOR_INTERNAL = ROOT + ".processor.internal..";
    static final String BUILTINS = ROOT + ".spi.builtins..";
    static final String REACTOR = ROOT + ".reactor..";
    static final String REACTOR_BLOCKING = ROOT + ".reactorblocking..";
    static final String TEST_FOUNDATION = ROOT + ".test..";

    /** The engine graph package other-module code must never touch. */
    static final String ENGINE_GRAPH = ROOT + ".processor.internal.graph..";

    static final String[] STRATEGY_MODULES = {BUILTINS, REACTOR, REACTOR_BLOCKING};

    /**
     * Packages decomposed by change decompose-engine-stages (design D6): every class in them is
     * individually testable, so the size ceiling applies here.
     */
    static final String[] DECOMPOSED_ENGINE_PACKAGES = {
        ROOT + ".processor.internal.stages.expand..", ROOT + ".processor.internal.stages.generate..", BUILTINS
    };

    /** Shaded third-party dependencies relocated to dodge a processorpath clash — not percolate's code. */
    static final String SHADED_LIB = ROOT + ".lib..";

    /**
     * Dagger's markers, under both spellings.
     *
     * <p>{@code processor} shades and relocates dagger into {@code io.github.joke.percolate.lib.dagger} at
     * its own {@code shadowJar} step, so the relocated name is what the published jar carries — which is
     * what the old union-classpath suite saw, since it resolved {@code project(':processor')} to that jar.
     * The runner instead evaluates a module's <em>own</em> source-set output, which is pre-shading, so the
     * unrelocated {@code dagger.*} name is what actually appears there. Both are accepted so the rules do
     * not silently depend on which artifact form is under evaluation.
     *
     * <p>{@code javax.annotation.processing.Generated} is deliberately absent: it is SOURCE-retention and
     * never reaches bytecode. {@code dagger.internal.DaggerGenerated} is CLASS-retention and does.
     */
    static final String[] DAGGER_GENERATED = {
        "dagger.internal.DaggerGenerated", ROOT + ".lib.dagger.internal.DaggerGenerated"
    };

    static final String[] DAGGER_PROVIDES = {"dagger.Provides", ROOT + ".lib.dagger.Provides"};
    static final String VISIBLE_FOR_TESTING = "org.jetbrains.annotations.VisibleForTesting";
    static final String LOMBOK_GENERATED = "lombok.Generated";

    private Packages() {}
}
