package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.spi.CreationDescriptor;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

public final class ConstructorAssignmentNode implements TargetAssignmentNode {
    private final TypeElement targetType;
    private final CreationDescriptor descriptor;

    public ConstructorAssignmentNode(TypeElement targetType, CreationDescriptor descriptor) {
        this.targetType = targetType;
        this.descriptor = descriptor;
    }

    @Override
    public TypeElement getTargetType() {
        return targetType;
    }

    public CreationDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public TypeMirror inType() {
        return targetType.asType();
    }

    @Override
    public TypeMirror outType() {
        return targetType.asType();
    }

    @Override
    public String toString() {
        return "Constructor(" + targetType.getSimpleName() + ")";
    }
}
