package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.model.DiscoveredMethod;
import lombok.Value;

@Value
public class SubMapOperation implements TransformOperation {
    DiscoveredMethod targetMethod;
}
