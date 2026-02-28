package io.github.joke.percolate.spi;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.github.joke.percolate.graph.node.MappingNode;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of MappingNodes to insert between two type-mismatched endpoints.
 * WiringStage splices these nodes into the graph, replacing the original FlowEdge.
 */
public final class ConversionFragment {

    private final List<MappingNode> nodes;

    public ConversionFragment(List<MappingNode> nodes) {
        this.nodes = unmodifiableList(new ArrayList<>(nodes));
    }

    public static ConversionFragment of(MappingNode... nodes) {
        return new ConversionFragment(asList(nodes));
    }

    public List<MappingNode> getNodes() {
        return nodes;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
