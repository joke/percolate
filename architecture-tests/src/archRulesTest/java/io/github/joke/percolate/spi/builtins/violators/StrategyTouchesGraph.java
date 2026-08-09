package io.github.joke.percolate.spi.builtins.violators;

import io.github.joke.percolate.processor.internal.graph.violators.GraphNode;
import io.github.joke.percolate.spi.ExpansionStrategy;

/** Violates: a strategy implementation may not touch the engine graph. */
public class StrategyTouchesGraph implements ExpansionStrategy {
    public GraphNode reach() {
        return new GraphNode();
    }
}
