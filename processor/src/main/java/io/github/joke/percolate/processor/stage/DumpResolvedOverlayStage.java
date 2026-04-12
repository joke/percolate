package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.WARNING;

import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.graph.AccessEdge;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import io.github.joke.percolate.processor.transform.TransformResolution;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.PackageElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.DefaultAttribute;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpResolvedOverlayStage {

    private final ProcessorOptions options;
    private final Filer filer;
    private final Messager messager;

    public void execute(final MappingGraph mappingGraph, final ResolvedModel resolvedModel) {
        if (!options.isDebugGraphs()) {
            return;
        }
        final var mapperType = mappingGraph.getMapperType();
        final var packageElement = Objects.requireNonNull(
                (PackageElement) mapperType.getEnclosingElement(), "mapper must have enclosing package");
        final var packageName = packageElement.getQualifiedName().toString();
        final var mapperName = mapperType.getSimpleName().toString();

        for (final var method : mappingGraph.getMethods()) {
            final var graph = Objects.requireNonNull(mappingGraph.getMethodGraphs().get(method));
            final var mappings =
                    Objects.requireNonNull(resolvedModel.getMethodMappings().get(method));
            final var edgeSummaries = buildEdgeSummaryIndex(graph, mappings);
            final var baseName = mapperName + "_" + methodName(method) + "_resolved";
            try {
                GraphExportSupport.writeGraph(
                        graph,
                        Object::toString,
                        edge -> {
                            if (edge instanceof AccessEdge) {
                                return Map.of("label", DefaultAttribute.createAttribute("access"));
                            }
                            if (edge instanceof MappingEdge) {
                                final var summary = edgeSummaries.get(edge);
                                final var label = summary != null ? "map: " + summary : "mapping";
                                return Map.of("label", DefaultAttribute.createAttribute(label));
                            }
                            return Map.of("label", DefaultAttribute.createAttribute(edge.toString()));
                        },
                        filer,
                        packageName,
                        baseName,
                        options.getDebugGraphsFormat());
            } catch (final IOException e) {
                messager.printMessage(
                        WARNING, "Failed to write overlay graph for " + baseName + ": " + e.getMessage());
            }
        }
    }

    private static IdentityHashMap<Object, String> buildEdgeSummaryIndex(
            final DefaultDirectedGraph<Object, Object> graph,
            final List<ResolvedMapping> mappings) {
        final var byTarget = mappings.stream()
                .collect(Collectors.toMap(ResolvedMapping::getTargetName, m -> m, (a, b) -> a));
        final var index = new IdentityHashMap<Object, String>();
        for (final var edge : graph.edgeSet()) {
            if (!(edge instanceof MappingEdge)) {
                continue;
            }
            final var target = graph.getEdgeTarget(edge);
            if (!(target instanceof PropertyNode)) {
                continue;
            }
            final var mapping = byTarget.get(((PropertyNode) target).getName());
            if (mapping != null) {
                index.put(edge, transformSummary(mapping));
            }
        }
        return index;
    }

    private static String transformSummary(final ResolvedMapping mapping) {
        final @Nullable TransformResolution resolution = mapping.getTransformResolution();
        if (resolution == null || resolution.getPath() == null) {
            return "unresolved";
        }
        final var edges = resolution.getPath().getEdgeList();
        if (edges.isEmpty()) {
            return "direct";
        }
        final var sourceLabel = resolution.getPath().getStartVertex().getLabel();
        final var targetLabel = resolution.getPath().getEndVertex().getLabel();
        final var strategies = edges.stream()
                .map(e -> e.getStrategy().getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        return sourceLabel + "\u2192" + targetLabel + " (" + strategies + ")";
    }

    private static String methodName(final MappingMethodModel method) {
        return method.getMethod().getSimpleName().toString();
    }
}
