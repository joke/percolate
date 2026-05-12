package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
@SuppressWarnings("PMD.AtLeastOneConstructor")
public class BridgeGraphQuery {

    public List<Edge> collectFlavorTwoSeedEdges(final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getFrom().getLoc() instanceof SourceLocation)
                .filter(e -> e.getTo().getLoc() instanceof TargetLocation)
                .collect(toUnmodifiableList());
    }

    public List<Edge> collectSubSeedEdges(final MapperGraph graph) {
        return graph.edges().filter(e -> e.getKind() == EdgeKind.SUB_SEED).collect(toUnmodifiableList());
    }

    public List<Edge> collectElementScopeSeedEdges(final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SEED)
                .filter(e -> e.getFrom().getLoc() instanceof ElementLocation)
                .filter(e -> e.getTo().getLoc() instanceof ElementLocation)
                .collect(toUnmodifiableList());
    }

    @Nullable
    public Node resolveRealisedCounterpart(final Node seedNode, final MapperGraph graph) {
        if (seedNode.getType().isPresent()) {
            return seedNode;
        }

        final var markerTarget = findTypedMarkerTarget(seedNode, graph);
        if (markerTarget != null) {
            return markerTarget;
        }

        return findTypedRealisedSource(seedNode, graph);
    }

    @Nullable
    public TypeMirror resolveNodeType(final Node node, final MapperGraph graph) {
        if (node.getType().isPresent()) {
            return node.getType().get();
        }
        final var markerTarget = findTypedMarkerTarget(node, graph);
        if (markerTarget != null && markerTarget.getType().isPresent()) {
            return markerTarget.getType().get();
        }
        final var realisedSource = findTypedRealisedSource(node, graph);
        if (realisedSource != null && realisedSource.getType().isPresent()) {
            return realisedSource.getType().get();
        }
        return null;
    }

    @Nullable
    private static Node findTypedMarkerTarget(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.MARKER)
                .filter(e -> e.getFrom().equals(seedNode))
                .map(Edge::getTo)
                .filter(target -> target.getType().isPresent())
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static Node findTypedRealisedSource(final Node seedNode, final MapperGraph graph) {
        return graph.edges()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> e.getTo().equals(seedNode))
                .map(Edge::getFrom)
                .filter(source -> source.getType().isPresent())
                .findFirst()
                .orElse(null);
    }
}
