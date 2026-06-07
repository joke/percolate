package io.github.joke.percolate.processor.graph;

import java.util.List;
import lombok.Value;

@Value
public class TargetPath {
    List<String> segments;

    public static TargetPath of(final String segment) {
        if (segment == null || segment.isEmpty()) {
            return new TargetPath(List.of());
        }
        return new TargetPath(List.of(segment));
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
