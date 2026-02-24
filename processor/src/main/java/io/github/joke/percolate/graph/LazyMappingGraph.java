package io.github.joke.percolate.graph;

import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.ConversionProvider.Conversion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.GraphType;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

/**
 * A lazy-expanding graph that wraps a base DirectedWeightedMultigraph and materializes
 * ConversionEdges on demand when outgoingEdgesOf is called during algorithm traversal.
 */
public final class LazyMappingGraph extends AbstractGraph<GraphNode, GraphEdge> {

    private final DirectedWeightedMultigraph<GraphNode, GraphEdge> base;
    private final List<ConversionProvider> providers;
    private final @Nullable ProcessingEnvironment env;
    private final int maxDepth;
    private final Set<GraphNode> expanded = new HashSet<>();
    private int currentDepth;

    public LazyMappingGraph(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> base,
            List<ConversionProvider> providers,
            @Nullable ProcessingEnvironment env,
            int maxDepth) {
        this.base = base;
        this.providers = providers;
        this.env = env;
        this.maxDepth = maxDepth;
        this.currentDepth = 0;
    }

    @Override
    public Set<GraphEdge> outgoingEdgesOf(GraphNode vertex) {
        if (!expanded.contains(vertex) && currentDepth < maxDepth) {
            expanded.add(vertex);
            expandConversions(vertex);
        }
        return base.outgoingEdgesOf(vertex);
    }

    private void expandConversions(GraphNode vertex) {
        @Nullable TypeMirror sourceType = getTypeOf(vertex);
        if (sourceType == null) {
            return;
        }
        currentDepth++;
        for (ConversionProvider provider : providers) {
            List<Conversion> conversions = provider.possibleConversions(sourceType, env);
            for (Conversion conversion : conversions) {
                TypeNode targetNode = new TypeNode(
                        conversion.getTargetType(), conversion.getTargetType().toString());
                base.addVertex(targetNode);
                if (!base.containsEdge(vertex, targetNode)) {
                    base.addEdge(vertex, targetNode, conversion.getEdge());
                }
            }
        }
        currentDepth--;
    }

    private static @Nullable TypeMirror getTypeOf(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    // --- Delegate all other Graph methods to base ---

    @Override
    public @Nullable Supplier<GraphNode> getVertexSupplier() {
        return base.getVertexSupplier();
    }

    @Override
    public @Nullable Supplier<GraphEdge> getEdgeSupplier() {
        return base.getEdgeSupplier();
    }

    @Override
    public Set<GraphNode> vertexSet() {
        return base.vertexSet();
    }

    @Override
    public Set<GraphEdge> edgeSet() {
        return base.edgeSet();
    }

    @Override
    public GraphNode getEdgeSource(GraphEdge edge) {
        return base.getEdgeSource(edge);
    }

    @Override
    public GraphNode getEdgeTarget(GraphEdge edge) {
        return base.getEdgeTarget(edge);
    }

    @Override
    public Set<GraphEdge> incomingEdgesOf(GraphNode vertex) {
        return base.incomingEdgesOf(vertex);
    }

    @Override
    public int degreeOf(GraphNode vertex) {
        return base.degreeOf(vertex);
    }

    @Override
    public int inDegreeOf(GraphNode vertex) {
        return base.inDegreeOf(vertex);
    }

    @Override
    public int outDegreeOf(GraphNode vertex) {
        return base.outDegreeOf(vertex);
    }

    @Override
    public Set<GraphEdge> edgesOf(GraphNode vertex) {
        return base.edgesOf(vertex);
    }

    @Override
    public Set<GraphEdge> getAllEdges(GraphNode source, GraphNode target) {
        return base.getAllEdges(source, target);
    }

    @Override
    public GraphEdge getEdge(GraphNode source, GraphNode target) {
        return base.getEdge(source, target);
    }

    @Override
    public boolean containsEdge(GraphEdge edge) {
        return base.containsEdge(edge);
    }

    @Override
    public boolean containsEdge(GraphNode source, GraphNode target) {
        return base.containsEdge(source, target);
    }

    @Override
    public boolean containsVertex(GraphNode vertex) {
        return base.containsVertex(vertex);
    }

    @Override
    public GraphType getType() {
        return base.getType();
    }

    @Override
    public double getEdgeWeight(GraphEdge edge) {
        return base.getEdgeWeight(edge);
    }

    @Override
    public GraphEdge addEdge(GraphNode source, GraphNode target) {
        return base.addEdge(source, target);
    }

    @Override
    public boolean addEdge(GraphNode source, GraphNode target, GraphEdge edge) {
        return base.addEdge(source, target, edge);
    }

    @Override
    public GraphNode addVertex() {
        return base.addVertex();
    }

    @Override
    public boolean addVertex(GraphNode vertex) {
        return base.addVertex(vertex);
    }

    @Override
    public boolean removeEdge(GraphEdge edge) {
        return base.removeEdge(edge);
    }

    @Override
    public GraphEdge removeEdge(GraphNode source, GraphNode target) {
        return base.removeEdge(source, target);
    }

    @Override
    public boolean removeVertex(GraphNode vertex) {
        return base.removeVertex(vertex);
    }

    @Override
    public void setEdgeWeight(GraphEdge edge, double weight) {
        base.setEdgeWeight(edge, weight);
    }

    public DirectedWeightedMultigraph<GraphNode, GraphEdge> getBase() {
        return base;
    }
}
