package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class TargetLocation implements Location {
    TargetPath path;

    @Override
    public String encode() {
        return "tgt[" + path + "]";
    }

    @Override
    public String segment() {
        return "tgt[" + path + "]";
    }
}
