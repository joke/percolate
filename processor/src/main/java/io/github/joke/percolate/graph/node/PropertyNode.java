package io.github.joke.percolate.graph.node;

import io.github.joke.percolate.model.Property;
import java.util.Objects;

public final class PropertyNode implements GraphNode {

    private final GraphNode parent;
    private final Property property;

    public PropertyNode(GraphNode parent, Property property) {
        this.parent = parent;
        this.property = property;
    }

    public GraphNode getParent() {
        return parent;
    }

    public Property getProperty() {
        return property;
    }

    public String name() {
        return property.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PropertyNode)) {
            return false;
        }
        PropertyNode that = (PropertyNode) o;
        return Objects.equals(parent, that.parent) && Objects.equals(name(), that.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, name());
    }

    @Override
    public String toString() {
        return "PropertyNode{" + name() + " : " + property.getType() + "}";
    }
}
