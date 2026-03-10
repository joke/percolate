package io.github.joke.percolate.graph.node;

import javax.lang.model.type.TypeMirror;

/** Interface for all nodes in the mapping data-flow graph. */
public interface MappingNode {
    TypeMirror inType();

    TypeMirror outType();
}
