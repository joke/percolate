package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public final class ElementLocation implements Location {

    @Override
    public String encode() {
        return "elem";
    }

    @Override
    public String segment() {
        return "elem";
    }
}
