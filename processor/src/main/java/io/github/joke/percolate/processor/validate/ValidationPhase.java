package io.github.joke.percolate.processor.validate;

import io.github.joke.percolate.processor.graph.MapperGraph;
import javax.lang.model.element.TypeElement;

public interface ValidationPhase {
    void apply(MapperGraph graph, TypeElement typeElement);
}
