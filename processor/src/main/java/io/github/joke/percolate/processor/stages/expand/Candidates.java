package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.TargetLocation;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class Candidates {

    private Candidates() {}

    static List<Node> fromView(final ExpansionGroup group, final Node frontier) {
        return group.getView().vertexSet().stream()
                .filter(n -> !n.equals(frontier))
                .filter(n -> !(n.getLoc() instanceof TargetLocation))
                .sorted(Comparator.comparing(Node::id))
                .collect(Collectors.toUnmodifiableList());
    }
}
