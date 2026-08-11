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

    private Packages() {}
}
