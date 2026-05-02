package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public final class TargetLocation implements Location {
    TargetPath path;

    @Override
    public String encode() {
        return "tgt[" + path + "]";
    }
}
