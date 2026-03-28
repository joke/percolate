package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.model.DiscoveredModel;
import io.github.joke.percolate.processor.model.MapDirective;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jgrapht.graph.DefaultDirectedGraph;

public final class BuildGraphStage {

    @Inject
    BuildGraphStage() {}

    public StageResult<MappingGraph> execute(final DiscoveredModel discoveredModel) {
        final DefaultDirectedGraph<PropertyNode, MappingEdge> graph = new DefaultDirectedGraph<>(MappingEdge.class);
        final List<Diagnostic> errors = new ArrayList<>();

        for (final DiscoveredMethod method : discoveredModel.getMethods()) {
            final Map<String, SourcePropertyNode> sourceNodes = new LinkedHashMap<>();
            final Map<String, TargetPropertyNode> targetNodes = new LinkedHashMap<>();

            for (final ReadAccessor accessor : method.getSourceProperties().values()) {
                final SourcePropertyNode node =
                        new SourcePropertyNode(accessor.getName(), accessor.getType(), accessor);
                sourceNodes.put(accessor.getName(), node);
                graph.addVertex(node);
            }

            for (final WriteAccessor accessor : method.getTargetProperties().values()) {
                final TargetPropertyNode node =
                        new TargetPropertyNode(accessor.getName(), accessor.getType(), accessor);
                targetNodes.put(accessor.getName(), node);
                graph.addVertex(node);
            }

            for (final MapDirective directive : method.getOriginal().getDirectives()) {
                final SourcePropertyNode sourceNode = sourceNodes.get(directive.getSource());
                final TargetPropertyNode targetNode = targetNodes.get(directive.getTarget());

                if (sourceNode == null) {
                    errors.add(new Diagnostic(
                            method.getOriginal().getMethod(),
                            "Unknown source property: " + directive.getSource(),
                            ERROR));
                    continue;
                }

                if (targetNode == null) {
                    errors.add(new Diagnostic(
                            method.getOriginal().getMethod(),
                            "Unknown target property: " + directive.getTarget(),
                            ERROR));
                    continue;
                }

                graph.addEdge(sourceNode, targetNode, new MappingEdge(MappingEdge.Type.DIRECT));
            }
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(
                new MappingGraph(discoveredModel.getMapperType(), discoveredModel.getMethods(), graph));
    }
}
