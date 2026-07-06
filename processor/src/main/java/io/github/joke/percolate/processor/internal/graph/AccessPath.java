package io.github.joke.percolate.processor.internal.graph;

import java.util.List;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
public class AccessPath {
    List<String> segments;

    public static AccessPath of(final String segment) {
        return new AccessPath(List.of(segment));
    }

    /** The dot-separated segments of {@code path}, or none for a {@code null}/empty path. */
    public static List<String> splitDotted(final @Nullable String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
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
