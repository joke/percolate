package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Codegen;
import java.util.List;
import java.util.Optional;
import lombok.Value;

/**
 * Adds one {@link Operation} atomically: the Operation vertex, its output {@link Dep} edge into the produced
 * {@link io.github.joke.percolate.processor.graph.Value} (named by {@link #output}), and exactly one port edge
 * per {@link PortBinding} — each feeding Value resolved through the {@link AddValue} get-or-create rule. A
 * present {@link #childScope} declaration makes the landed Operation scope-owning, minting the child scope's
 * param/return-root Values with it.
 */
@Value
public class AddOperation implements GraphDelta {
    String label;
    String strategyFqn;
    Codegen codegen;
    int weight;
    boolean partial;
    List<PortBinding> ports;
    AddValue output;
    Optional<ChildScopeDecl> childScope;
}
