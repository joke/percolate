package io.github.joke.percolate.processor.model;

import java.util.Map;
import lombok.Value;

@Value
public class DiscoveredMethod {
    MappingMethodModel original;
    Map<String, ReadAccessor> sourceProperties;
    Map<String, WriteAccessor> targetProperties;
}
