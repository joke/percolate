package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.MapperGraph;

public interface ExpansionPhase {
    boolean apply(MapperGraph graph);
}
