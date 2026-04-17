package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Edge contributed by a {@link TypeTransformStrategy} representing a type conversion step.
 *
 * <p>The {@code proposalTemplate} is pre-stored from the strategy's {@code TransformProposal}
 * at edge-construction time so that {@code OptimizePathStage} can materialise the template
 * without needing the original context.
 *
 * <p>The {@code codeTemplate} field starts {@code null} and is populated exactly once by
 * {@code OptimizePathStage} for edges that lie on the selected path. Off-path edges remain
 * {@code null} after optimization.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class TypeTransformEdge extends ValueEdge {

    private final TypeTransformStrategy strategy;
    private final TypeMirror input;
    private final TypeMirror output;

    /** The {@code CodeTemplate} from the originating {@code TransformProposal}. */
    private final CodeTemplate proposalTemplate;

    @Nullable
    private CodeTemplate codeTemplate;

    /**
     * Materialises the code template exactly once from the stored {@link #proposalTemplate}.
     *
     * @throws IllegalStateException if called more than once
     */
    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitTypeTransform(this);
    }

    public void resolveTemplate() {
        if (this.codeTemplate != null) {
            throw new IllegalStateException(
                    "codeTemplate already set on " + this + "; OptimizePathStage may not call resolveTemplate twice");
        }
        this.codeTemplate = proposalTemplate;
    }
}
