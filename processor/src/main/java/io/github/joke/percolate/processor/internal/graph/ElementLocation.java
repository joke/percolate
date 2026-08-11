package io.github.joke.percolate.processor.internal.graph;

import lombok.Value;

import static io.github.joke.percolate.processor.internal.graph.Location.Role.LEAF;

@Value
public class ElementLocation implements Location {
    String name;

    public ElementLocation() {
        this("element");
    }

    public ElementLocation(final String name) {
        this.name = name;
    }

    @Override
    public Role role() {
        return LEAF;
    }

    @Override
    public String segment() {
        return "elem(" + name + ")";
    }

    @Override
    public String slotName() {
        return name;
    }
}
