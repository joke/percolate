package io.github.joke.percolate.spi;

import static java.util.Arrays.asList;

import io.github.joke.percolate.graph.node.MappingNode;
import java.util.List;

/**
 * An ordered list of MappingNodes to insert between two type-mismatched endpoints.
 * WiringStage splices these nodes into the graph, replacing the original FlowEdge.
 */
public final class ConversionFragment {

    private final List<MappingNode> nodes;

    public ConversionFragment(final List<MappingNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    public static ConversionFragment of(final MappingNode... nodes) {
        return new ConversionFragment(asList(nodes));
    }

    public List<MappingNode> getNodes() {
        return nodes;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
