package io.github.joke.percolate.graph.edge;

import io.github.joke.percolate.model.Property;

/** Edge from a TypeNode to a PropertyNode — getting a property. */
public final class PropertyAccessEdge implements GraphEdge {

    private final Property property;

    public PropertyAccessEdge(Property property) {
        this.property = property;
    }

    public Property getProperty() {
        return property;
    }

    @Override
    public String toString() {
        return "PropertyAccess{" + property.getName() + "}";
    }
}
