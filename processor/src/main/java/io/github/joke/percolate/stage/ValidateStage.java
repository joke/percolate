package io.github.joke.percolate.stage;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    ValidateStage(Messager messager) {
        this.messager = messager;
    }

    public boolean execute(MethodRegistry registry) {
        return registry.entries().values().stream()
                .filter(entry -> !entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null)
                .map(this::validateEntry)
                .reduce(false, Boolean::logicalOr);
    }

    private boolean validateEntry(RegistryEntry entry) {
        Graph<MappingNode, FlowEdge> graph = requireNonNull(entry.getGraph());
        MethodDefinition signature = requireNonNull(entry.getSignature());

        List<SourceNode> sources = graph.vertexSet().stream()
                .filter(SourceNode.class::isInstance)
                .map(SourceNode.class::cast)
                .collect(toList());

        Optional<ConstructorAssignmentNode> sinkOpt = graph.vertexSet().stream()
                .filter(ConstructorAssignmentNode.class::isInstance)
                .map(ConstructorAssignmentNode.class::cast)
                .findFirst();

        if (sources.isEmpty() || !sinkOpt.isPresent()) {
            return false;
        }
        ConstructorAssignmentNode sink = sinkOpt.get();

        boolean deadEndErrors = reportDeadEnds(graph, sources, sink, signature);
        boolean paramErrors = reportMissingParams(graph, sink, signature);
        return deadEndErrors || paramErrors;
    }

    private boolean reportDeadEnds(
            Graph<MappingNode, FlowEdge> graph,
            List<SourceNode> sources,
            ConstructorAssignmentNode sink,
            MethodDefinition signature) {
        Set<MappingNode> forwardReachable = forwardReachable(graph, sources);
        Set<MappingNode> canReachSink = backwardReachable(graph, sink);

        Set<MappingNode> broken = new LinkedHashSet<>(forwardReachable);
        broken.removeAll(canReachSink);

        boolean hasErrors = false;
        for (MappingNode node : broken) {
            if (node instanceof PropertyAccessNode) {
                PropertyAccessNode prop = (PropertyAccessNode) node;
                messager.printMessage(
                        ERROR,
                        "Property '" + prop.getPropertyName()
                                + "' (type " + prop.getOutType()
                                + ") has no conversion path to "
                                + sink.getTargetType().getSimpleName()
                                + " in " + signature.getName());
                hasErrors = true;
            }
        }
        return hasErrors;
    }

    private boolean reportMissingParams(
            Graph<MappingNode, FlowEdge> graph, ConstructorAssignmentNode sink, MethodDefinition signature) {
        Set<String> mappedSlots = graph.incomingEdgesOf(sink).stream()
                .map(FlowEdge::getSlotName)
                .filter(Objects::nonNull)
                .collect(toSet());

        boolean hasErrors = false;
        for (Property param : sink.getDescriptor().getParameters()) {
            if (!mappedSlots.contains(param.getName())) {
                messager.printMessage(
                        ERROR,
                        "No source mapping for constructor parameter '"
                                + param.getName()
                                + "' of " + sink.getTargetType().getSimpleName()
                                + " in " + signature.getName());
                hasErrors = true;
            }
        }
        return hasErrors;
    }

    private static Set<MappingNode> forwardReachable(Graph<MappingNode, FlowEdge> graph, List<SourceNode> sources) {
        Set<MappingNode> reachable = new LinkedHashSet<>();
        for (SourceNode source : sources) {
            new DepthFirstIterator<>(graph, source).forEachRemaining(reachable::add);
        }
        return reachable;
    }

    private static Set<MappingNode> backwardReachable(
            Graph<MappingNode, FlowEdge> graph, ConstructorAssignmentNode sink) {
        Set<MappingNode> reachable = new LinkedHashSet<>();
        new DepthFirstIterator<>(new EdgeReversedGraph<>(graph), sink).forEachRemaining(reachable::add);
        return reachable;
    }
}
