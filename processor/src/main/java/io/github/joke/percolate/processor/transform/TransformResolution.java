package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import lombok.Value;
import org.jgrapht.GraphPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jspecify.annotations.Nullable;

@Value
public class TransformResolution {
    DefaultDirectedGraph<TypeNode, TransformEdge> explorationGraph;

    @Nullable
    GraphPath<TypeNode, TransformEdge> path;
}
