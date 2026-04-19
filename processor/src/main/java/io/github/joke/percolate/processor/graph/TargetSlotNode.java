package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Leaf node representing a constructor argument or setter slot on the target type.
 *
 * <p>One {@code TargetSlotNode} is created per {@code MappingAssignment} by
 * {@code BuildValueGraphStage}. Target slots are always leaves — they have no outgoing edges.
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public final class TargetSlotNode extends ValueNode {

    private final String name;
    private final TypeMirror type;
    private final WriteAccessor writeAccessor;

    @Override
    public CodeBlock compose(final Map<ValueEdge, CodeBlock> inputs, final ComposeKind kind) {
        if (kind != ComposeKind.EXPRESSION) {
            throw new IllegalStateException("TargetSlotNode supports EXPRESSION only, got: " + kind);
        }
        if (inputs.size() != 1) {
            throw new IllegalStateException("TargetSlotNode expects exactly one incoming edge, got: " + inputs.size());
        }
        return inputs.values().iterator().next();
    }
}
