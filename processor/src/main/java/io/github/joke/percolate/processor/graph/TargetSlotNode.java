package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Leaf node representing a constructor argument (or, legacy, a setter) slot on the target type.
 *
 * <p>One {@code TargetSlotNode} is created per {@code MappingAssignment} by
 * {@code BuildValueGraphStage}. In the demand-driven model, slots have exactly one outgoing edge:
 * the slot-to-root edge feeding the partition's {@code TargetRootNode}.
 *
 * <p>{@link #getParamIndex()} records the zero-based position of this slot in the target
 * constructor's parameter list. It is populated by {@code ConstructorCallStrategy}; callers that
 * still build slots directly (legacy paths) SHOULD set it via {@link #setParamIndex(int)} after
 * construction.
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public final class TargetSlotNode extends ValueNode {

    private final String name;
    private final TypeMirror type;
    private final WriteAccessor writeAccessor;

    @Setter
    private int paramIndex;

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
