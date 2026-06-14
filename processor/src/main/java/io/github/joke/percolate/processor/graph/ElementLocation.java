package io.github.joke.percolate.processor.graph;

import lombok.Value;

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
        return Role.ELEMENT;
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
