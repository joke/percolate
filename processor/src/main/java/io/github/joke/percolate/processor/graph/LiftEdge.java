package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.transform.CodeTemplate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jgrapht.GraphPath;
import org.jspecify.annotations.Nullable;

/**
 * Edge wrapping a sub-path under a container or null-safe scope.
 *
 * <p>The {@code innerPath} contains the per-element transformation applied inside the lift;
 * all vertices and edges in {@code innerPath} SHALL also be present in the enclosing
 * {@code ValueGraph}.
 *
 * <p>The {@code codeTemplate} starts {@code null} (same rule as {@link TypeTransformEdge}) and
 * is populated exactly once by {@code OptimizePathStage}.
 *
 * <p>{@link LiftKind#NULL_CHECK} is declared but never constructed in this refactor.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class LiftEdge extends ValueEdge {

    private final LiftKind kind;
    private final GraphPath<ValueNode, ValueEdge> innerPath;

    @Nullable
    private CodeTemplate codeTemplate;

    /**
     * Populates the code template exactly once.
     *
     * @throws IllegalStateException if called more than once
     */
    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitLift(this);
    }

    public void resolveTemplate(final CodeTemplate codeTemplate) {
        if (this.codeTemplate != null) {
            throw new IllegalStateException(
                    "codeTemplate already set on " + this + "; OptimizePathStage may not call resolveTemplate twice");
        }
        this.codeTemplate = codeTemplate;
    }
}
