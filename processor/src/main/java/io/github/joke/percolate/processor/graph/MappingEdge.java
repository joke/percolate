package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.MapOptKey;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;

/**
 * Edge in the symbolic property graph representing a mapping from a source
 * property leaf to a target property.
 */
@Getter
@ToString
public final class MappingEdge {
    private final Map<MapOptKey, String> options;

    public MappingEdge() {
        this.options = Collections.emptyMap();
    }

    public MappingEdge(final Map<MapOptKey, String> options) {
        this.options = options;
    }
}
