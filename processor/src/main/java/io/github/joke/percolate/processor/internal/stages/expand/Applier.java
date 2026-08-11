package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddOperation;
import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import org.jetbrains.annotations.VisibleForTesting;

// The single graph-mutation site during expansion (design D10). Every AddOperation an expander decides on lands
// through here, delegating to MapperGraph.apply — which get-or-creates the feeding/output Values and lands the
// Operation atomically with its port edges.
//
// Graph cycles (e.g. a box∘unbox pair between x:int and x:Integer) are permitted and harmless: the cost
// extraction fold is well-founded — its cycle guard gives a not-yet-resolved Value infinite cost, so a Value is
// never reachable through a cycle containing itself (proven by ExtractedPlanSpec). There is therefore no
// rollback and no rejection (the design D10 assertion-only cycle check is unnecessary once well-foundedness
// holds).
final class Applier {

    @VisibleForTesting
    Operation apply(final MapperGraph graph, final AddOperation delta) {
        return graph.apply(delta);
    }

    // Lands a bare AddValue (a root demand, or a lazily-materialised parameter leaf).
    @VisibleForTesting
    Value apply(final MapperGraph graph, final AddValue delta) {
        return graph.apply(delta);
    }
}
