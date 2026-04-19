package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Anonymous typed value mid-chain, e.g. the {@code Optional<String>} produced by
 * {@code OptionalWrapStrategy} between a property node and the target slot.
 *
 * <p>Two {@code TypedValueNode}s with identical {@code TypeMirror} (compared by string
 * representation) are considered equal so that BFS reuses them on the same graph rather
 * than creating duplicates.
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public final class TypedValueNode extends ValueNode {

    @EqualsAndHashCode.Include
    private final String typeString;

    @ToString.Include
    private final TypeMirror type;

    private final String label;

    public TypedValueNode(final TypeMirror type, final String label) {
        this.typeString = type.toString();
        this.type = type;
        this.label = label;
    }

    @Override
    public CodeBlock compose(final Map<ValueEdge, CodeBlock> inputs, final ComposeKind kind) {
        if (kind != ComposeKind.EXPRESSION) {
            throw new IllegalStateException("TypedValueNode supports EXPRESSION only, got: " + kind);
        }
        if (inputs.size() != 1) {
            throw new IllegalStateException("TypedValueNode expects exactly one incoming edge, got: " + inputs.size());
        }
        return inputs.values().iterator().next();
    }
}
