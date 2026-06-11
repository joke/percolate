package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class SourceLocation implements Location {
    AccessPath path;

    @Override
    public String segment() {
        return "src[" + path + "]";
    }

    @Override
    public String slotName() {
        return path.lastSegment();
    }
}
