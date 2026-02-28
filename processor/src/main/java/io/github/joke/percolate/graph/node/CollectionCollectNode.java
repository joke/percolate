package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Collects elements into a target collection type. Inline — no helper method registered. */
public final class CollectionCollectNode implements MappingNode {
    private final TypeMirror targetCollectionType;
    private final TypeMirror elementType;

    public CollectionCollectNode(TypeMirror targetCollectionType, TypeMirror elementType) {
        this.targetCollectionType = targetCollectionType;
        this.elementType = elementType;
    }

    public TypeMirror getTargetCollectionType() { return targetCollectionType; }
    public TypeMirror getElementType() { return elementType; }

    @Override
    public String toString() { return "CollectionCollect(" + elementType + "->" + targetCollectionType + ")"; }
}
