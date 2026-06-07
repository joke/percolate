package io.github.joke.percolate.processor.graph;

import java.util.List;
import lombok.Value;

@Value
public class AccessPath {
    List<String> segments;

    public static AccessPath of(final String segment) {
        return new AccessPath(List.of(segment));
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
