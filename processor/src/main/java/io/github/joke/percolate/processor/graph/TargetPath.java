package io.github.joke.percolate.processor.graph;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;

@Value
public final class TargetPath {
    List<String> segments;

    public static TargetPath of(final String segment) {
        if (segment == null || segment.isEmpty()) {
            return new TargetPath(List.of());
        }
        return new TargetPath(List.of(segment));
    }

    public TargetPath append(final String segment) {
        return new TargetPath(
                Stream.concat(segments.stream(), Stream.of(segment)).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
