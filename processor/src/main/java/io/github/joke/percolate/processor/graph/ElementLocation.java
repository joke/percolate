package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public final class ElementLocation implements Location {
    String role;

    public ElementLocation() {
        this("element");
    }

    public ElementLocation(final String role) {
        this.role = role;
    }

    @Override
    public String encode() {
        return "elem(" + role + ")";
    }

    @Override
    public String segment() {
        return "elem(" + role + ")";
    }
}
