package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Codegen;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.MemberRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

// Adds one Operation atomically: the Operation vertex, its output Dep edge into the produced
// io.github.joke.percolate.processor.internal.graph.Value (named by .output), and exactly one port edge per
// PortBinding — each feeding Value resolved through the AddValue get-or-create rule. A present .childScope
// declaration makes the landed Operation scope-owning, minting the child scope's param/return-root Values with
// it. .consumed carries the DirectiveInputs the emitting strategy stamped as read (see
// io.github.joke.percolate.spi.OperationSpec.getConsumed()). .memberRequests carries the class-level member
// requests the emitting strategy declared (see io.github.joke.percolate.spi.OperationSpec.getMemberRequests()).
@Value
public class AddOperation implements GraphDelta {
    String label;
    Codegen codegen;
    int weight;
    boolean partial;
    List<PortBinding> ports;
    AddValue output;
    Optional<ChildScopeDecl> childScope;
    Set<DirectiveInput> consumed;
    List<MemberRequest> memberRequests;
}
