package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.MapOptKey;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
public class ResolvedMapping {
    List<ReadAccessor> sourceChain;
    String sourceName;

    @Nullable
    WriteAccessor targetAccessor;

    String targetName;

    @Nullable
    TransformResolution transformResolution;

    @Nullable
    AccessResolutionFailure failure;

    Map<MapOptKey, String> options;

    public boolean isResolved() {
        return failure == null
                && transformResolution != null
                && transformResolution.getPath() != null;
    }

    public List<TransformEdge> getEdges() {
        if (transformResolution == null || transformResolution.getPath() == null) {
            return List.of();
        }
        return transformResolution.getPath().getEdgeList();
    }
}
