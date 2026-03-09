package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.model.MethodDefinition;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import org.jgrapht.Graph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.DepthFirstIterator;

@RoundScoped
public final class ValidateStage {

    private final Messager messager;

    @Inject
    ValidateStage(final Messager messager) {
        this.messager = messager;
    }

    public boolean execute(final MethodRegistry registry) {
        return registry.entries().values().stream()
                .filter(entry -> !entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null)
                .map(this::entryHasErrors)
                .reduce(false, Boolean::logicalOr);
    }

    private boolean entryHasErrors(final RegistryEntry entry) {
        final var graph = entry.getGraph();
        final var signature = entry.getSignature();
        if (graph == null || signature == null) {
            return false;
        }

        final var sinkOpt = graph.vertexSet().stream()
                .filter(ConstructorAssignmentNode.class::isInstance)
                .map(ConstructorAssignmentNode.class::cast)
                .findFirst();

        if (sinkOpt.isEmpty()) {
            return false;
        }
        final var sink = sinkOpt.get();

        final var deadEndErrors = reportDeadEnds(graph, sink, signature);
        final var paramErrors = reportMissingParams(graph, sink, signature);
        return deadEndErrors || paramErrors;
    }

    private boolean reportDeadEnds(
            final Graph<MappingNode, FlowEdge> graph,
            final ConstructorAssignmentNode sink,
            final MethodDefinition signature) {
        final var canReachSink = backwardReachable(graph, sink);
        return graph.vertexSet().stream()
                        .filter(PropertyAccessNode.class::isInstance)
                        .map(PropertyAccessNode.class::cast)
                        .filter(prop -> !canReachSink.contains(prop))
                        .peek(prop -> messager.printMessage(
                                ERROR,
                                "Property '" + prop.getPropertyName()
                                        + "' (type " + prop.getOutType()
                                        + ") has no conversion path to "
                                        + sink.getTargetType().getSimpleName()
                                        + " in " + signature.getName()))
                        .count()
                > 0;
    }

    private boolean reportMissingParams(
            final Graph<MappingNode, FlowEdge> graph,
            final ConstructorAssignmentNode sink,
            final MethodDefinition signature) {
        final var mappedSlots = graph.incomingEdgesOf(sink).stream()
                .map(FlowEdge::getSlotName)
                .filter(Objects::nonNull)
                .collect(toSet());
        return sink.getDescriptor().getParameters().stream()
                        .filter(param -> !mappedSlots.contains(param.getName()))
                        .peek(param -> messager.printMessage(
                                ERROR,
                                "No source mapping for constructor parameter '"
                                        + param.getName()
                                        + "' of " + sink.getTargetType().getSimpleName()
                                        + " in " + signature.getName()))
                        .count()
                > 0;
    }

    private static Set<MappingNode> backwardReachable(
            final Graph<MappingNode, FlowEdge> graph, final ConstructorAssignmentNode sink) {
        final var reachable = new LinkedHashSet<MappingNode>();
        new DepthFirstIterator<>(new EdgeReversedGraph<>(graph), sink).forEachRemaining(reachable::add);
        return reachable;
    }
}
