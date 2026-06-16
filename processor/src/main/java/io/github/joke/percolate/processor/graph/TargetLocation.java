package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class TargetLocation implements Location {
    TargetPath path;

    @Override
    public Role role() {
        return Role.FREE;
    }

    @Override
    public String segment() {
        return "tgt[" + path + "]";
    }

    @Override
    public String slotName() {
        return path.lastSegment();
    }

    @Override
    public boolean isReturnRoot() {
        return path.getSegments().isEmpty();
    }
}
