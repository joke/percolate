package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddOperation;
import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.ChildScopeDecl;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.PortBinding;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.OperationSpec;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Constructs and applies one {@link AddOperation} (design D6/D9 of change {@code target-driven-engine}, decomposed
 * out of {@code ExpandStage.Driver} by {@code decompose-engine-stages}): the single {@code AddOperation}-construction
 * primitive behind both the producer-landing and accessor-descent walks, plus the tiny {@link AddValue} conversions
 * ({@link #outputOf}/{@link #reuse}) both walks share to name an existing {@link Value} in a delta.
 */
@RequiredArgsConstructor
final class OperationLander {

    private final MapperGraph graph;
    private final Applier applier;

    /** Builds and applies the {@link AddOperation} for {@code spec} bound by {@code ports}, producing {@code output}. */
    Operation landOperation(final OperationSpec spec, final List<PortBinding> ports, final AddValue output) {
        return apply(new AddOperation(
                spec.getLabel(),
                spec.getCodegen(),
                spec.getWeight(),
                spec.isPartial(),
                ports,
                output,
                spec.getChildScope()
                        .map(child -> new ChildScopeDecl(
                                child.getElementIn(),
                                child.getElementInNullness(),
                                child.getElementOut(),
                                child.getElementOutNullness()))));
    }

    /** Applies {@code delta}, landing its Operation vertex and port/output edges atomically. */
    Operation apply(final AddOperation delta) {
        return applier.apply(graph, delta);
    }

    /** Names {@code value} as an operation's output, by its existing identity key. */
    AddValue outputOf(final Value value) {
        return new AddValue(value.getScope(), value.getLoc(), value.type(), value.nullness());
    }

    /** Names {@code value} as a port's feeding source, by its existing identity key. */
    AddValue reuse(final Value value) {
        return new AddValue(value.getScope(), value.getLoc(), value.type(), value.nullness());
    }
}
