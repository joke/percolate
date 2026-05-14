package io.github.joke.percolate.processor.graph;

import java.util.stream.Stream;

public interface GraphSource {
    Stream<Node> nodes();

    Stream<Edge> edges();
}
