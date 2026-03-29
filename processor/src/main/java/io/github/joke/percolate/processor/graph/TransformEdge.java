package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import lombok.Value;

@Value
public class TransformEdge {
    TypeTransformStrategy strategy;
    CodeTemplate codeTemplate;

    @Override
    public String toString() {
        return strategy.getClass().getSimpleName();
    }
}
