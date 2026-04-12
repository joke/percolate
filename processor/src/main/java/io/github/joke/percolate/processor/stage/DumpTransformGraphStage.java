package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.WARNING;

import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import io.github.joke.percolate.processor.transform.TransformResolution;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.PackageElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.DefaultAttribute;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpTransformGraphStage {

    private final ProcessorOptions options;
    private final Filer filer;
    private final Messager messager;

    public void execute(final ResolvedModel resolvedModel) {
        if (!options.isDebugGraphs()) {
            return;
        }
        final var mapperType = resolvedModel.getMapperType();
        final var packageElement = Objects.requireNonNull(
                (PackageElement) mapperType.getEnclosingElement(), "mapper must have enclosing package");
        final var packageName = packageElement.getQualifiedName().toString();
        final var mapperName = mapperType.getSimpleName().toString();

        for (final var method : resolvedModel.getMethods()) {
            final var mappings =
                    Objects.requireNonNull(resolvedModel.getMethodMappings().get(method));
            final var merged = mergeExplorationGraphs(mappings);
            if (merged.vertexSet().isEmpty()) {
                continue;
            }
            final var winningEdges = collectWinningEdges(mappings);
            final var baseName = mapperName + "_" + methodName(method) + "_transform";
            final var format = options.getDebugGraphsFormat();
            try {
                GraphExportSupport.writeGraph(
                        merged,
                        TypeNode::getLabel,
                        edge -> buildTransformEdgeAttributes(edge, winningEdges, format),
                        filer,
                        packageName,
                        baseName,
                        format);
            } catch (final IOException e) {
                messager.printMessage(
                        WARNING, "Failed to write transform graph for " + baseName + ": " + e.getMessage());
            }
        }
    }

    private static DefaultDirectedGraph<TypeNode, TransformEdge> mergeExplorationGraphs(
            final List<ResolvedMapping> mappings) {
        final var merged = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge.class);
        final Map<String, TypeNode> nodeIndex = new LinkedHashMap<>();

        for (final var mapping : mappings) {
            final @Nullable TransformResolution resolution = mapping.getTransformResolution();
            if (resolution == null) {
                continue;
            }
            final var explorationGraph = resolution.getExplorationGraph();
            for (final var vertex : explorationGraph.vertexSet()) {
                nodeIndex.computeIfAbsent(vertex.getLabel(), k -> {
                    merged.addVertex(vertex);
                    return vertex;
                });
            }
            for (final var edge : explorationGraph.edgeSet()) {
                final var source = nodeIndex.get(explorationGraph.getEdgeSource(edge).getLabel());
                final var target = nodeIndex.get(explorationGraph.getEdgeTarget(edge).getLabel());
                if (source != null && target != null && !merged.containsEdge(source, target)) {
                    merged.addEdge(source, target, edge);
                }
            }
        }
        return merged;
    }

    private static Set<TransformEdge> collectWinningEdges(final List<ResolvedMapping> mappings) {
        final var winning = new java.util.LinkedHashSet<TransformEdge>();
        for (final var mapping : mappings) {
            final @Nullable TransformResolution resolution = mapping.getTransformResolution();
            if (resolution == null || resolution.getPath() == null) {
                continue;
            }
            winning.addAll(resolution.getPath().getEdgeList());
        }
        return winning;
    }

    private static Map<String, org.jgrapht.nio.Attribute> buildTransformEdgeAttributes(
            final TransformEdge edge, final Set<TransformEdge> winningEdges, final String format) {
        final var label = edge.getStrategy().getClass().getSimpleName();
        if (winningEdges.contains(edge)) {
            if ("dot".equals(format.toLowerCase(Locale.ROOT))) {
                return Map.of(
                        "label", DefaultAttribute.createAttribute(label),
                        "style", DefaultAttribute.createAttribute("bold"));
            }
            return Map.of(
                    "label", DefaultAttribute.createAttribute(label),
                    "winning", DefaultAttribute.createAttribute(true));
        }
        return Map.of("label", DefaultAttribute.createAttribute(label));
    }

    private static String methodName(final MappingMethodModel method) {
        return method.getMethod().getSimpleName().toString();
    }
}
