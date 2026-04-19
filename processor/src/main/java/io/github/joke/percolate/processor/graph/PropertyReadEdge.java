package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.transform.CodeTemplate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Edge from a {@link SourceParamNode} or {@link PropertyNode} to a {@link PropertyNode},
 * representing a getter or field read.
 *
 * <p>Carries its own {@link CodeTemplate} (e.g. {@code "$L.getFoo()"} for a getter or
 * {@code "$L.foo"} for a field), populated at construction by {@code BuildValueGraphStage} from the
 * discovered {@link io.github.joke.percolate.processor.model.ReadAccessor}. The template is
 * applied by {@code GenerateStage} during topological evaluation — the rendering concern no longer
 * lives on the target {@link PropertyNode}.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class PropertyReadEdge extends ValueEdge {

    private final CodeTemplate template;

    @Override
    public <R> R accept(final ValueEdgeVisitor<R> visitor) {
        return visitor.visitPropertyRead(this);
    }
}
