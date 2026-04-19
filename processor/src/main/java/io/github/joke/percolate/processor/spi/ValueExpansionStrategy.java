package io.github.joke.percolate.processor.spi;

import java.util.Optional;

/**
 * Unified SPI for contributing nodes and edges to a {@code MapperGraph} during demand-driven
 * expansion. Replaces the prior {@code SourcePropertyDiscovery}, {@code TargetPropertyDiscovery},
 * and {@code TypeTransformStrategy} SPIs.
 *
 * <h2>Contract</h2>
 * <p>{@code BuildValueGraphStage} pops a demand from its worklist and consults every registered
 * {@code ValueExpansionStrategy} in descending {@link #priority()} order. Each strategy returns:
 *
 * <ul>
 *   <li>{@code Optional.empty()} — the strategy declines the demand; the expander moves on to the
 *       next strategy in priority order.
 *   <li>{@code Optional.of(subgraph)} — the strategy contributes the fragment; the expander
 *       merges it into the mapper graph, counts one against the per-mapper budget (1000), and
 *       terminates strategy consultation for this demand.
 * </ul>
 *
 * <p>Strategies MUST be pure: given the same demand and context, they SHALL return an equivalent
 * {@link Subgraph}. Strategies MUST NOT mutate the parent graph directly — that is the expander's
 * job.
 *
 * <h2>Registration</h2>
 * <p>Built-in implementations are registered via {@code @AutoService(ValueExpansionStrategy.class)}.
 * Custom implementations SHALL provide a {@code META-INF/services/
 * io.github.joke.percolate.processor.spi.ValueExpansionStrategy} entry on the annotation-processor
 * classpath; they are loaded by {@code ServiceLoader} alongside the built-ins.
 *
 * <h2>Priority constants</h2>
 * <p>Built-in priorities (documented here so downstream strategies can pick a reasonable rank):
 *
 * <ul>
 *   <li>{@code 200} — {@code ConstructorCallStrategy}
 *   <li>{@code 150} — {@code RoutableMethodStrategy}
 *   <li>{@code 100} — {@code GetterReadStrategy} (source-side getters)
 *   <li>{@code 100} — {@code DirectAssignableStrategy}, container collect/stream strategies, optional
 *       wrappers, temporal bridges
 *   <li>{@code 50}  — {@code FieldReadStrategy} (source-side public fields)
 * </ul>
 */
public interface ValueExpansionStrategy {

    /** Higher values are consulted first; ties are broken by {@code ServiceLoader} load order. */
    int priority();

    /**
     * Attempt to satisfy the given demand. Returns {@link Optional#empty()} to decline; otherwise
     * returns the contributed {@link Subgraph} to merge.
     */
    Optional<Subgraph> expand(ExpansionDemand demand, ExpansionContext ctx);
}
