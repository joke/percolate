package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.transform.DirectOperation;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import io.github.joke.percolate.processor.transform.SubMapOperation;
import io.github.joke.percolate.processor.transform.TransformNode;
import io.github.joke.percolate.processor.transform.UnresolvedOperation;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.util.Types;

public final class ResolveTransformsStage {

    private final Types types;

    @Inject
    ResolveTransformsStage(final Types types) {
        this.types = types;
    }

    public StageResult<ResolvedModel> execute(final MappingGraph mappingGraph) {
        final Map<DiscoveredMethod, List<ResolvedMapping>> methodMappings = new LinkedHashMap<>();

        for (final var entry : mappingGraph.getMethodGraphs().entrySet()) {
            final var method = entry.getKey();
            final var graph = entry.getValue();
            final List<ResolvedMapping> resolved = new ArrayList<>();

            for (final var edge : graph.edgeSet()) {
                final var sourceNode = (SourcePropertyNode) graph.getEdgeSource(edge);
                final var targetNode = (TargetPropertyNode) graph.getEdgeTarget(edge);
                final var sourceType = sourceNode.getType();
                final var targetType = targetNode.getType();

                final TransformNode transformNode;
                if (types.isAssignable(sourceType, targetType)) {
                    transformNode = new TransformNode(sourceType, targetType, new DirectOperation());
                } else {
                    final var sibling = findSiblingMethod(mappingGraph.getMethods(), sourceType, targetType);
                    if (sibling.isPresent()) {
                        transformNode = new TransformNode(sourceType, targetType, new SubMapOperation(sibling.get()));
                    } else {
                        transformNode = new TransformNode(
                                sourceType, targetType, new UnresolvedOperation(sourceType, targetType));
                    }
                }

                resolved.add(new ResolvedMapping(sourceNode, targetNode, List.of(transformNode)));
            }

            methodMappings.put(method, List.copyOf(resolved));
        }

        return StageResult.success(
                new ResolvedModel(mappingGraph.getMapperType(), mappingGraph.getMethods(), methodMappings));
    }

    private Optional<DiscoveredMethod> findSiblingMethod(
            final List<DiscoveredMethod> methods,
            final javax.lang.model.type.TypeMirror sourceType,
            final javax.lang.model.type.TypeMirror targetType) {
        return methods.stream()
                .filter(m -> types.isAssignable(sourceType, m.getOriginal().getSourceType()))
                .filter(m -> types.isAssignable(m.getOriginal().getTargetType(), targetType))
                .findFirst();
    }
}
