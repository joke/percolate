package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.Value;
import lombok.experimental.UtilityClass;

/**
 * Horn unit propagation over the bipartite graph (design D6): a {@code Value} is SAT iff at least one producer
 * {@code Operation} is SAT; an {@code Operation} is SAT iff all of its port Values are SAT and — for a
 * scope-owning Operation — its child return-root is SAT. Base cases are supply-root Values (parameter roots and
 * container element param-roots: a {@link SourceLocation}/{@link ElementLocation} Value with no producer) and
 * zero-port Operations.
 *
 * <p>It is computed as a monotone fixpoint (mark newly-SAT vertices until none change), so derivations are
 * well-founded by construction: a Value never becomes SAT through a cycle containing itself (a cyclic producer's
 * port is never marked before the Value it would satisfy).
 */
@UtilityClass
final class HornSat {

    /** Recomputes the SAT predicate over {@code graph} from scratch. */
    static void propagate(final MapperGraph graph) {
        graph.clearSat();
        var changed = true;
        while (changed) {
            changed = false;
            for (final var value : graph.values().toArray(Value[]::new)) {
                if (!graph.isSat(value) && valueSat(graph, value)) {
                    graph.markSat(value);
                    changed = true;
                }
            }
            for (final var operation : graph.operations().toArray(Operation[]::new)) {
                if (!graph.isSat(operation) && operationSat(graph, operation)) {
                    graph.markSat(operation);
                    changed = true;
                }
            }
        }
    }

    private static boolean valueSat(final MapperGraph graph, final Value value) {
        final var baseCase =
                isSupplyRoot(value) && graph.producersOf(value).findAny().isEmpty();
        return baseCase || graph.producersOf(value).anyMatch(graph::isSat);
    }

    private static boolean operationSat(final MapperGraph graph, final Operation operation) {
        final var portsSat = operation.getPorts().stream().allMatch(port -> graph.portSource(operation, port.getName())
                .map(graph::isSat)
                .orElse(false));
        return portsSat
                && operation
                        .getChildScope()
                        .map(child -> graph.isSat(child.getReturnRoot()))
                        .orElse(true);
    }

    private static boolean isSupplyRoot(final Value value) {
        return value.getLoc() instanceof SourceLocation || value.getLoc() instanceof ElementLocation;
    }
}
