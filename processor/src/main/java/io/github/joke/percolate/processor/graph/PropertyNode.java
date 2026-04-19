package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Intermediate node representing a source property reached via getter or field access.
 *
 * <p>Equality is by {@code name} and {@code typeString} so that two {@code PropertyNode}s for the
 * same property on the same type compare equal and JGraphT treats them as the same vertex (enabling
 * shared-path deduplication for e.g. {@code customer.name} and {@code customer.age}).
 *
 * <p>The node carries no accessor — the access template lives on the incoming {@link
 * PropertyReadEdge}.
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public final class PropertyNode extends ValueNode {

    @EqualsAndHashCode.Include
    @ToString.Include
    private final String name;

    @EqualsAndHashCode.Include
    private final String typeString;

    private final TypeMirror type;

    public PropertyNode(final String name, final TypeMirror type) {
        this.name = name;
        this.typeString = type.toString();
        this.type = type;
    }

    @Override
    public CodeBlock compose(final Map<ValueEdge, CodeBlock> inputs, final ComposeKind kind) {
        if (kind != ComposeKind.EXPRESSION) {
            throw new IllegalStateException("PropertyNode supports EXPRESSION only, got: " + kind);
        }
        if (inputs.size() != 1) {
            throw new IllegalStateException("PropertyNode expects exactly one incoming edge, got: " + inputs.size());
        }
        return inputs.values().iterator().next();
    }
}
