package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.ReadAccessor;
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

    private final ReadAccessor readAccessor;

    public PropertyNode(final String name, final TypeMirror type, final ReadAccessor readAccessor) {
        this.name = name;
        this.typeString = type.toString();
        this.type = type;
        this.readAccessor = readAccessor;
    }
}
