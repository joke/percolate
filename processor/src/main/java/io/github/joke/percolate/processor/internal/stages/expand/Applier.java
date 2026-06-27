package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddOperation;
import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;

/**
 * The single graph-mutation site during expansion (design D10). Every {@link AddOperation} an expander decides on
 * lands through here, delegating to {@code MapperGraph.apply} — which get-or-creates the feeding/output
 * {@code Value}s and lands the Operation atomically with its port edges.
 *
 * <p>Graph cycles (e.g. a box∘unbox pair between {@code x:int} and {@code x:Integer}) are permitted and harmless:
 * the cost extraction fold is well-founded — its cycle guard gives a not-yet-resolved Value infinite cost, so a
 * Value is never reachable through a cycle containing itself (proven by {@code ExtractedPlanSpec}). There is
 * therefore no rollback and no rejection (the design D10 assertion-only cycle check is unnecessary once
 * well-foundedness holds).
 */
final class Applier {

    Operation apply(final MapperGraph graph, final AddOperation delta) {
        return graph.apply(delta);
    }

    /** Lands a bare {@link AddValue} (a root demand, or a lazily-materialised parameter leaf). */
    Value apply(final MapperGraph graph, final AddValue delta) {
        return graph.apply(delta);
    }
}
