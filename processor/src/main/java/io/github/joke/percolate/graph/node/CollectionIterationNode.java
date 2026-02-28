package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Iterates a collection, producing individual elements. Inline — no helper method registered. */
public final class CollectionIterationNode implements MappingNode {
    private final TypeMirror collectionType;
    private final TypeMirror elementType;

    public CollectionIterationNode(TypeMirror collectionType, TypeMirror elementType) {
        this.collectionType = collectionType;
        this.elementType = elementType;
    }

    public TypeMirror getCollectionType() { return collectionType; }
    public TypeMirror getElementType() { return elementType; }

    @Override
    public String toString() { return "CollectionIteration(" + collectionType + "->" + elementType + ")"; }
}
