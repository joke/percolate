package io.github.joke.percolate.processor.internal.graph;

import java.util.List;
import lombok.Value;

@Value
public class AccessPath {
    List<String> segments;

    public static AccessPath of(final String segment) {
        return new AccessPath(List.of(segment));
    }

    /** The last segment, or the empty string when the path is empty. */
    public String lastSegment() {
        return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
