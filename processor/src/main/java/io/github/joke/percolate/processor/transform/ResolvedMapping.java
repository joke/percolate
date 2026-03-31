package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.List;
import lombok.Value;
import org.jgrapht.GraphPath;
import org.jspecify.annotations.Nullable;

@Value
public class ResolvedMapping {
    List<ReadAccessor> sourceChain;
    String sourceName;

    @Nullable
    WriteAccessor targetAccessor;

    String targetName;

    @Nullable
    GraphPath<TypeNode, TransformEdge> path;

    @Nullable
    AccessResolutionFailure failure;

    public boolean isResolved() {
        return failure == null && path != null;
    }

    public List<TransformEdge> getEdges() {
        if (path == null) {
            return List.of();
        }
        return path.getEdgeList();
    }
}
