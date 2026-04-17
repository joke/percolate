package io.github.joke.percolate.processor.match;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueNode;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.GraphPath;
import org.jspecify.annotations.Nullable;

/**
 * Per-assignment output of {@code ResolvePathStage}.
 *
 * <p>Carries the original {@link MappingAssignment}, the shortest resolved path (or {@code null}
 * if no path was found), and an optional {@link ResolutionFailure} populated when the
 * source access chain could not be walked (recorded by {@code BuildValueGraphStage}).
 *
 * <p>{@link #isResolved()} is the primary gate: callers should check it before consuming the path.
 * {@link #getReadChainEdges()} is a convenience accessor for downstream code generation.
 */
@Getter
@RequiredArgsConstructor
public final class ResolvedAssignment {

    private final MappingAssignment assignment;

    @Nullable
    private final GraphPath<ValueNode, ValueEdge> path;

    @Nullable
    private final ResolutionFailure failure;

    /**
     * Returns {@code true} iff a path was found and no access-chain failure was recorded.
     */
    public boolean isResolved() {
        return path != null && failure == null;
    }

    /**
     * Returns the sub-list of edges on {@link #path} that are {@link PropertyReadEdge}s, in order.
     * Returns an empty list when the assignment is unresolved.
     */
    public List<PropertyReadEdge> getReadChainEdges() {
        if (path == null) {
            return List.of();
        }
        return path.getEdgeList().stream()
                .filter(e -> e instanceof PropertyReadEdge)
                .map(e -> (PropertyReadEdge) e)
                .collect(toUnmodifiableList());
    }
}
