package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public final class SourceLocation implements Location {
    AccessPath path;

    @Override
    public String encode() {
        return "src[" + path + "]";
    }

    @Override
    public String segment() {
        return "src[" + path + "]";
    }
}
