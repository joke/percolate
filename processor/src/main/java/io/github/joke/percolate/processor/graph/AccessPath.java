package io.github.joke.percolate.processor.graph;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;

@Value
public final class AccessPath {
    List<String> segments;

    public static AccessPath of(final String segment) {
        return new AccessPath(List.of(segment));
    }

    public AccessPath append(final String segment) {
        return new AccessPath(
                Stream.concat(segments.stream(), Stream.of(segment)).collect(Collectors.toUnmodifiableList()));
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
