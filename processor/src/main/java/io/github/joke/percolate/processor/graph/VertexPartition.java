package io.github.joke.percolate.processor.graph;

import java.util.Set;
import lombok.Value;

/**
 * Per-method slice of a {@link MapperGraph}. Identifies the method's source parameter, its target
 * construction root, and the set of vertices that participate in the method's expansion.
 *
 * <p>Vertices MAY appear in multiple partitions (e.g. a shared {@link TypedValueNode} for the
 * same {@code TypeMirror}). Edges, in contrast, belong to exactly one partition — the partition
 * whose {@link #getMethodVertices()} contains both endpoints.
 */
@Value
public class VertexPartition {
    SourceParamNode sourceParam;
    TargetRootNode targetRoot;
    Set<ValueNode> methodVertices;
}
