package io.github.joke.percolate.processor.graph;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public interface GraphSource {
    Stream<Node> nodes();

    Stream<Edge> edges();
}
