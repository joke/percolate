package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic.Kind;
import org.jgrapht.nio.dot.DOTExporter;

public final class ValidateStage {

    @Inject
    ValidateStage() {}

    public StageResult<MappingGraph> execute(final MappingGraph mappingGraph) {
        final List<Diagnostic> errors = new ArrayList<>();
        final var graph = mappingGraph.getGraph();

        for (final PropertyNode node : graph.vertexSet()) {
            if (!(node instanceof TargetPropertyNode)) {
                continue;
            }

            final int inDegree = graph.inDegreeOf(node);

            if (inDegree == 0) {
                errors.add(new Diagnostic(
                        mappingGraph.getMapperType(), "Unmapped target property: " + node.name(), Kind.ERROR));
            }

            if (inDegree > 1) {
                errors.add(new Diagnostic(
                        mappingGraph.getMapperType(),
                        "Conflicting mappings for target property: " + node.name(),
                        Kind.ERROR));
            }
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(mappingGraph);
    }

    @SuppressWarnings("UnusedMethod")
    public String exportDot(final MappingGraph mappingGraph) {
        final DOTExporter<PropertyNode, MappingEdge> exporter = new DOTExporter<>(PropertyNode::name);
        final StringWriter writer = new StringWriter();
        exporter.exportGraph(mappingGraph.getGraph(), writer);
        return writer.toString();
    }
}
