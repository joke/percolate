package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.MemberRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/**
 * Adds one {@link Operation} atomically: the Operation vertex, its output {@link Dep} edge into the produced
 * {@link io.github.joke.percolate.processor.internal.graph.Value} (named by {@link #output}), and exactly one port edge
 * per {@link PortBinding} — each feeding Value resolved through the {@link AddValue} get-or-create rule. A
 * present {@link #childScope} declaration makes the landed Operation scope-owning, minting the child scope's
 * param/return-root Values with it. {@link #consumedOptionKeys} carries the {@code @Map} option keys the emitting
 * strategy stamped as read (see {@link io.github.joke.percolate.spi.OperationSpec#getConsumedOptionKeys()}).
 * {@link #memberRequests} carries the class-level member requests the emitting strategy declared (see
 * {@link io.github.joke.percolate.spi.OperationSpec#getMemberRequests()}).
 */
@Value
public class AddOperation implements GraphDelta {
    String label;
    Codegen codegen;
    int weight;
    boolean partial;
    List<PortBinding> ports;
    AddValue output;
    Optional<ChildScopeDecl> childScope;
    Set<String> consumedOptionKeys;
    List<MemberRequest> memberRequests;
}
