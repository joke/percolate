package io.github.joke.percolate.stage;

import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.model.MethodDefinition;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

public final class RegistryEntry {
    private final @Nullable MethodDefinition signature;
    private final @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> graph;

    public RegistryEntry(
            @Nullable MethodDefinition signature, @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> graph) {
        this.signature = signature;
        this.graph = graph;
    }

    /** null for user-provided default methods (opaque). */
    public @Nullable MethodDefinition getSignature() {
        return signature;
    }

    /** null for opaque methods. Non-null for abstract and auto-generated methods. */
    public @Nullable DirectedWeightedMultigraph<MappingNode, FlowEdge> getGraph() {
        return graph;
    }

    public boolean isOpaque() {
        return graph == null;
    }
}
