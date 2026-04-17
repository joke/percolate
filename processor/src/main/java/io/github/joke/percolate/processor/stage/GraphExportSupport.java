package io.github.joke.percolate.processor.stage;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.annotation.processing.Filer;
import javax.tools.StandardLocation;
import lombok.experimental.UtilityClass;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.jgrapht.nio.json.JSONExporter;

@UtilityClass
class GraphExportSupport {

    <V, E> void writeGraph(
            final Graph<V, E> graph,
            final Function<V, String> nodeLabeler,
            final Function<E, Map<String, Attribute>> edgeAttributeProvider,
            final Filer filer,
            final String packageName,
            final String baseName,
            final String format)
            throws IOException {
        final var content = exportToString(graph, nodeLabeler, edgeAttributeProvider, format);
        final var fileName = baseName + extensionFor(format);
        final var resource = filer.createResource(StandardLocation.SOURCE_OUTPUT, packageName, fileName);
        try (final var writer = resource.openWriter()) {
            writer.write(content);
        }
    }

    private static <V, E> String exportToString(
            final Graph<V, E> graph,
            final Function<V, String> nodeLabeler,
            final Function<E, Map<String, Attribute>> edgeAttributeProvider,
            final String format) {
        final var sw = new StringWriter();
        switch (format.toLowerCase(Locale.ROOT)) {
            case "graphml":
                final var mlExporter = buildGraphMLExporter(nodeLabeler, edgeAttributeProvider);
                mlExporter.registerAttribute("label", GraphMLExporter.AttributeCategory.NODE, AttributeType.STRING, "");
                discoverEdgeAttributes(graph, edgeAttributeProvider)
                        .forEach((name, type) ->
                                mlExporter.registerAttribute(name, GraphMLExporter.AttributeCategory.EDGE, type, ""));
                mlExporter.exportGraph(graph, sw);
                break;
            case "json":
                buildJsonExporter(nodeLabeler, edgeAttributeProvider).exportGraph(graph, sw);
                break;
            default:
                buildDotExporter(nodeLabeler, edgeAttributeProvider).exportGraph(graph, sw);
        }
        return sw.toString();
    }

    private static <V, E> Map<String, AttributeType> discoverEdgeAttributes(
            final Graph<V, E> graph, final Function<E, Map<String, Attribute>> edgeAttributeProvider) {
        final Map<String, AttributeType> result = new LinkedHashMap<>();
        for (final var edge : graph.edgeSet()) {
            edgeAttributeProvider.apply(edge).forEach((name, attr) -> result.putIfAbsent(name, attr.getType()));
        }
        return result;
    }

    private static <V, E> DOTExporter<V, E> buildDotExporter(
            final Function<V, String> nodeLabeler, final Function<E, Map<String, Attribute>> edgeAttributeProvider) {
        final var counter = new AtomicInteger();
        final var exporter = new DOTExporter<V, E>(v -> "n" + counter.getAndIncrement());
        exporter.setVertexAttributeProvider(
                v -> Map.of("label", DefaultAttribute.createAttribute(nodeLabeler.apply(v))));
        exporter.setEdgeAttributeProvider(edgeAttributeProvider);
        return exporter;
    }

    private static <V, E> GraphMLExporter<V, E> buildGraphMLExporter(
            final Function<V, String> nodeLabeler, final Function<E, Map<String, Attribute>> edgeAttributeProvider) {
        final var counter = new AtomicInteger();
        final var exporter = new GraphMLExporter<V, E>(v -> "n" + counter.getAndIncrement());
        exporter.setVertexAttributeProvider(
                v -> Map.of("label", DefaultAttribute.createAttribute(nodeLabeler.apply(v))));
        exporter.setEdgeAttributeProvider(edgeAttributeProvider);
        return exporter;
    }

    private static <V, E> JSONExporter<V, E> buildJsonExporter(
            final Function<V, String> nodeLabeler, final Function<E, Map<String, Attribute>> edgeAttributeProvider) {
        final var counter = new AtomicInteger();
        final var exporter = new JSONExporter<V, E>(v -> "n" + counter.getAndIncrement());
        exporter.setVertexAttributeProvider(
                v -> Map.of("label", DefaultAttribute.createAttribute(nodeLabeler.apply(v))));
        exporter.setEdgeAttributeProvider(edgeAttributeProvider);
        return exporter;
    }

    private static String extensionFor(final String format) {
        switch (format.toLowerCase(Locale.ROOT)) {
            case "graphml":
                return ".graphml";
            case "json":
                return ".json";
            default:
                return ".dot";
        }
    }
}
