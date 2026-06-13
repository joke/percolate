package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.AddOperation;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Operation;

/**
 * The single graph-mutation site during expansion (design D10). Every {@link AddOperation} an expander decides on
 * lands through here, delegating to {@code MapperGraph.apply} — which get-or-creates the feeding/output
 * {@code Value}s and lands the Operation atomically with its port edges.
 *
 * <p>Graph cycles (e.g. a box∘unbox pair between {@code x:int} and {@code x:Integer}) are permitted and harmless:
 * Horn propagation is well-founded — a {@code Value} never becomes SAT through a cycle containing itself (proven
 * by {@code HornSatSpec}) — and cost extraction guards against cyclic derivations. There is therefore no rollback
 * and no rejection (the design D10 assertion-only cycle check is unnecessary once well-foundedness holds).
 */
final class Applier {

    Operation apply(final MapperGraph graph, final AddOperation delta) {
        return graph.apply(delta);
    }
}
