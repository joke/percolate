package io.github.joke.percolate.processor.internal.graph;

import lombok.Value;

import static io.github.joke.percolate.processor.internal.graph.Location.Role.FREE;

@Value
public class TargetLocation implements Location {
    TargetPath path;

    @Override
    public Role role() {
        return FREE;
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
