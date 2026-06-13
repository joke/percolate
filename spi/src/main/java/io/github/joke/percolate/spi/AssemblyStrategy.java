package io.github.joke.percolate.spi;

/**
 * Marker for a strategy that <em>assembles</em> a structured target from its parts (e.g. a constructor or builder
 * call): it produces the target by binding ports that correspond to the target's own declared members. Such a
 * strategy interprets the demand's declared-children goal spec ({@link Demand#declaredChildren()}) at emission
 * time — a constructor is a candidate only when its parameter-name set equals the declared-children set — so it
 * never fires a vacuous producer that would drop user mappings, and never recurses unboundedly through the
 * reachable type graph.
 *
 * <p>This is a routing/gating concern, not a separate result type: an assembly strategy is still a plain
 * {@link ExpansionStrategy} emitting ordinary {@link OperationSpec}s into the one unified loader list.
 */
public interface AssemblyStrategy extends ExpansionStrategy {}
