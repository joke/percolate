package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class ElementLocation implements Location {
    String role;

    public ElementLocation() {
        this("element");
    }

    public ElementLocation(final String role) {
        this.role = role;
    }

    @Override
    public String segment() {
        return "elem(" + role + ")";
    }

    @Override
    public String slotName() {
        return role;
    }
}
