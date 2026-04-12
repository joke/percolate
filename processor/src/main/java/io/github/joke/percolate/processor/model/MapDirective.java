package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.MapOptKey;
import java.util.Map;
import lombok.Value;

@Value
public class MapDirective {
    String source;
    String target;
    String using;
    Map<MapOptKey, String> options;
}
