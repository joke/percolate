package io.github.joke.percolate.processor.graph;

/**
 * Edge in the symbolic property graph representing access from a source node
 * to a nested property. Connects SourceRootNode or SourcePropertyNode to SourcePropertyNode.
 */
public final class AccessEdge {

    @Override
    public String toString() {
        return "AccessEdge";
    }
}
