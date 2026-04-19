package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Edge contributed by a {@link TypeTransformStrategy} representing a type conversion step.
 *
 * <p>The {@link CodeTemplate} is populated at edge construction by {@code BuildValueGraphStage}
 * from the originating {@link io.github.joke.percolate.processor.transform.TransformProposal} and
 * is never mutated thereafter.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class TypeTransformEdge extends ValueEdge {

    private final TypeTransformStrategy strategy;
    private final TypeMirror input;
    private final TypeMirror output;
    private final CodeTemplate codeTemplate;

    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitTypeTransform(this);
    }
}
