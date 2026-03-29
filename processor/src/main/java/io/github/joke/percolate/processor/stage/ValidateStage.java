package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.nio.dot.DOTExporter;

public final class ValidateStage {

    @Inject
    ValidateStage() {}

    public StageResult<MappingGraph> execute(final MappingGraph mappingGraph) {
        final List<Diagnostic> errors = new ArrayList<>();

        for (final var graph : mappingGraph.getMethodGraphs().values()) {
            final Set<String> unmappedSourceNames = graph.vertexSet().stream()
                    .filter(SourcePropertyNode.class::isInstance)
                    .filter(node -> graph.outDegreeOf(node) == 0)
                    .map(PropertyNode::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (final PropertyNode node : graph.vertexSet()) {
                if (!(node instanceof TargetPropertyNode)) {
                    continue;
                }

                final int inDegree = graph.inDegreeOf(node);

                if (inDegree == 0) {
                    errors.add(new Diagnostic(
                            mappingGraph.getMapperType(),
                            ErrorMessages.unmappedTargetProperty(
                                    node.getName(), mappingGraph.getMapperType(), unmappedSourceNames),
                            ERROR));
                }

                if (inDegree > 1) {
                    final Set<String> conflictingSources = graph.incomingEdgesOf(node).stream()
                            .map(graph::getEdgeSource)
                            .map(PropertyNode::getName)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    errors.add(new Diagnostic(
                            mappingGraph.getMapperType(),
                            ErrorMessages.conflictingMappings(
                                    node.getName(), mappingGraph.getMapperType(), conflictingSources),
                            ERROR));
                }
            }
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(mappingGraph);
    }

    @SuppressWarnings("UnusedMethod")
    public String exportDot(final MappingGraph mappingGraph) {
        final DOTExporter<PropertyNode, MappingEdge> exporter = new DOTExporter<>(PropertyNode::getName);
        final StringWriter writer = new StringWriter();
        mappingGraph.getMethodGraphs().values().forEach(graph -> exporter.exportGraph(graph, writer));
        return writer.toString();
    }
}
