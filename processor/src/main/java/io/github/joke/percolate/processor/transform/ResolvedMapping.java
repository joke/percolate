package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import java.util.List;
import lombok.Value;
import org.jgrapht.GraphPath;
import org.jspecify.annotations.Nullable;

@Value
public class ResolvedMapping {
    SourcePropertyNode source;
    TargetPropertyNode target;
    @Nullable GraphPath<TypeNode, TransformEdge> path;

    public boolean isResolved() {
        return path != null;
    }

    public List<TransformEdge> getEdges() {
        if (path == null) {
            return List.of();
        }
        return path.getEdgeList();
    }
}
