package io.github.joke.percolate.stage;

import static java.util.Collections.unmodifiableList;

import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.model.MapperDefinition;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.graph.DirectedWeightedMultigraph;

public final class GraphResult {

    private final DirectedWeightedMultigraph<GraphNode, GraphEdge> graph;
    private final List<MapperDefinition> mappers;

    public GraphResult(DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, List<MapperDefinition> mappers) {
        this.graph = graph;
        this.mappers = unmodifiableList(new ArrayList<>(mappers));
    }

    public DirectedWeightedMultigraph<GraphNode, GraphEdge> getGraph() {
        return graph;
    }

    public List<MapperDefinition> getMappers() {
        return mappers;
    }
}
