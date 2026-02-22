package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;

/** Edge from TypeNode to TypeNode — unresolved generic mapping placeholder. */
public final class GenericPlaceholderEdge implements GraphEdge {

    private final TypeMirror sourceType;
    private final TypeMirror targetType;

    public GenericPlaceholderEdge(TypeMirror sourceType, TypeMirror targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public TypeMirror getSourceType() {
        return sourceType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    @Override
    public String toString() {
        return "GenericPlaceholder{" + sourceType + " -> " + targetType + "}";
    }
}
