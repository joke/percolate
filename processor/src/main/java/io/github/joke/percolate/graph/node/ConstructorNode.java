package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.spi.CreationDescriptor;
import java.util.Objects;
import javax.lang.model.element.TypeElement;

public final class ConstructorNode implements GraphNode {

    private final TypeElement targetType;
    private final CreationDescriptor descriptor;

    public ConstructorNode(TypeElement targetType, CreationDescriptor descriptor) {
        this.targetType = targetType;
        this.descriptor = descriptor;
    }

    public TypeElement getTargetType() {
        return targetType;
    }

    public CreationDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConstructorNode)) {
            return false;
        }
        ConstructorNode that = (ConstructorNode) o;
        return Objects.equals(
                targetType.getQualifiedName().toString(),
                that.targetType.getQualifiedName().toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetType.getQualifiedName().toString());
    }

    @Override
    public String toString() {
        return "ConstructorNode{" + targetType.getQualifiedName() + "}";
    }
}
