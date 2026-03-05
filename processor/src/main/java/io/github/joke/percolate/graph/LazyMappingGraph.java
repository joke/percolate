package io.github.joke.percolate.graph;

import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.processing.ProcessingEnvironment;
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
    private final int maxDepth;
    private final Set<GraphNode> expanded = new HashSet<>();
    private int currentDepth;

    public LazyMappingGraph(
            final DirectedWeightedMultigraph<GraphNode, GraphEdge> base,
            final List<ConversionProvider> providers,
            final @Nullable ProcessingEnvironment env,
            final int maxDepth) {
        this.base = base;
        this.maxDepth = maxDepth;
        this.currentDepth = 0;
    }

    @Override
    public Set<GraphEdge> outgoingEdgesOf(final GraphNode vertex) {
        if (!expanded.contains(vertex) && currentDepth < maxDepth) {
            expanded.add(vertex);
            expandConversions();
        }
        return base.outgoingEdgesOf(vertex);
    }

    private void expandConversions() {
        // ConversionProvider interface redesigned in Task 7 — expansion now handled by WiringStage.
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
    public GraphNode getEdgeSource(final GraphEdge edge) {
        return base.getEdgeSource(edge);
    }

    @Override
    public GraphNode getEdgeTarget(final GraphEdge edge) {
        return base.getEdgeTarget(edge);
    }

    @Override
    public Set<GraphEdge> incomingEdgesOf(final GraphNode vertex) {
        return base.incomingEdgesOf(vertex);
    }

    @Override
    public int degreeOf(final GraphNode vertex) {
        return base.degreeOf(vertex);
    }

    @Override
    public int inDegreeOf(final GraphNode vertex) {
        return base.inDegreeOf(vertex);
    }

    @Override
    public int outDegreeOf(final GraphNode vertex) {
        return base.outDegreeOf(vertex);
    }

    @Override
    public Set<GraphEdge> edgesOf(final GraphNode vertex) {
        return base.edgesOf(vertex);
    }

    @Override
    public Set<GraphEdge> getAllEdges(final GraphNode source, final GraphNode target) {
        return base.getAllEdges(source, target);
    }

    @Override
    public GraphEdge getEdge(final GraphNode source, final GraphNode target) {
        return base.getEdge(source, target);
    }

    @Override
    public boolean containsEdge(final GraphEdge edge) {
        return base.containsEdge(edge);
    }

    @Override
    public boolean containsEdge(final GraphNode source, final GraphNode target) {
        return base.containsEdge(source, target);
    }

    @Override
    public boolean containsVertex(final GraphNode vertex) {
        return base.containsVertex(vertex);
    }

    @Override
    public GraphType getType() {
        return base.getType();
    }

    @Override
    public double getEdgeWeight(final GraphEdge edge) {
        return base.getEdgeWeight(edge);
    }

    @Override
    public GraphEdge addEdge(final GraphNode source, final GraphNode target) {
        return base.addEdge(source, target);
    }

    @Override
    public boolean addEdge(final GraphNode source, final GraphNode target, final GraphEdge edge) {
        return base.addEdge(source, target, edge);
    }

    @Override
    public GraphNode addVertex() {
        return base.addVertex();
    }

    @Override
    public boolean addVertex(final GraphNode vertex) {
        return base.addVertex(vertex);
    }

    @Override
    public boolean removeEdge(final GraphEdge edge) {
        return base.removeEdge(edge);
    }

    @Override
    public GraphEdge removeEdge(final GraphNode source, final GraphNode target) {
        return base.removeEdge(source, target);
    }

    @Override
    public boolean removeVertex(final GraphNode vertex) {
        return base.removeVertex(vertex);
    }

    @Override
    public void setEdgeWeight(final GraphEdge edge, final double weight) {
        base.setEdgeWeight(edge, weight);
    }

    public DirectedWeightedMultigraph<GraphNode, GraphEdge> getBase() {
        return base;
    }
}
