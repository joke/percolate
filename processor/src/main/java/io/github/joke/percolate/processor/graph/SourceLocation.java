package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class SourceLocation implements Location {

    private static final int SINGLE_SEGMENT = 1;

    AccessPath path;

    @Override
    public Role role() {
        return path.getSegments().size() > SINGLE_SEGMENT ? Role.ACCESS : Role.LEAF;
    }

    @Override
    public String segment() {
        return "src[" + path + "]";
    }

    @Override
    public String slotName() {
        return path.lastSegment();
    }
}
