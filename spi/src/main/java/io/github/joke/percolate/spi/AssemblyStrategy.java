package io.github.joke.percolate.spi;

/**
 * Marker for a strategy that <em>assembles</em> a structured target from its parts (e.g. a constructor or builder
 * call): it produces the target by binding sub-values that correspond to the target's own declared members. Such a
 * strategy is only meaningful at a frontier that already has those members pinned as pre-seeded target leaves, so
 * the driver invokes assembly strategies <b>only</b> when producing such an assembly root — never on an arbitrary
 * value frontier, where firing a constructor would recurse unboundedly through the reachable type graph.
 *
 * <p>This is a driver routing hint, not a separate result type or discovery mode: an assembly strategy is still a
 * plain {@link ExpansionStrategy} emitting ordinary {@link ExpansionStep}s into the one unified loader list. It
 * encodes WHERE the strategy may fire (scaffolding's concern), not a kind-ordering of results.
 */
public interface AssemblyStrategy extends ExpansionStrategy {}
