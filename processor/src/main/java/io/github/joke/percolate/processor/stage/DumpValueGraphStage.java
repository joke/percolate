package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.WARNING;

import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.graph.LiftEdge;
import io.github.joke.percolate.processor.graph.NullWidenEdge;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.SourceParamNode;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.graph.TypeTransformEdge;
import io.github.joke.percolate.processor.graph.TypedValueNode;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueGraphResult;
import io.github.joke.percolate.processor.graph.ValueNode;
import io.github.joke.percolate.processor.match.MethodMatching;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;

/**
 * Debug stage: exports one {@code DefaultDirectedGraph<ValueNode, ValueEdge>} per mapper method to
 * a DOT / GraphML / JSON file, controlled by {@link ProcessorOptions#isDebugGraphs()}.
 *
 * <p>Fires after {@code BuildValueGraphStage}. Fails-soft: any {@link IOException} is logged via
 * {@link Messager} and the pipeline continues.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpValueGraphStage {

    private final ProcessorOptions options;
    private final Filer filer;
    private final Messager messager;

    public void execute(final TypeElement mapperType, final ValueGraphResult valueGraphResult) {
        if (!options.isDebugGraphs()) {
            return;
        }
        final var packageElement = Objects.requireNonNull(
                (PackageElement) mapperType.getEnclosingElement(), "mapper must have enclosing package");
        final var packageName = packageElement.getQualifiedName().toString();
        final var mapperName = mapperType.getSimpleName().toString();
        final var format = options.getDebugGraphsFormat();

        for (final var entry : valueGraphResult.getGraphs().entrySet()) {
            final var matching = entry.getKey();
            final var graph = entry.getValue();
            if (graph.vertexSet().isEmpty()) {
                continue;
            }
            final var baseName = mapperName + "_" + methodName(matching) + "_valuegraph";
            try {
                GraphExportSupport.writeGraph(
                        graph,
                        DumpValueGraphStage::nodeLabel,
                        DumpValueGraphStage::edgeAttributes,
                        filer,
                        packageName,
                        baseName,
                        format);
            } catch (final IOException e) {
                messager.printMessage(WARNING, "Failed to write value graph for " + baseName + ": " + e.getMessage());
            }
        }
    }

    private static String nodeLabel(final ValueNode node) {
        if (node instanceof SourceParamNode) {
            return "param:" + ((SourceParamNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return "prop:" + ((PropertyNode) node).getName();
        }
        if (node instanceof TypedValueNode) {
            return ((TypedValueNode) node).getType().toString();
        }
        if (node instanceof TargetSlotNode) {
            return "slot:" + ((TargetSlotNode) node).getName();
        }
        return node.toString();
    }

    private static Map<String, Attribute> edgeAttributes(final ValueEdge edge) {
        final String label;
        if (edge instanceof PropertyReadEdge) {
            label = "read";
        } else if (edge instanceof TypeTransformEdge) {
            label = ((TypeTransformEdge) edge).getStrategy().getClass().getSimpleName();
        } else if (edge instanceof LiftEdge) {
            label = "lift(" + ((LiftEdge) edge).getKind() + ")";
        } else if (edge instanceof NullWidenEdge) {
            label = "nullWiden";
        } else {
            label = edge.toString();
        }
        return Map.of("label", DefaultAttribute.createAttribute(label));
    }

    private static String methodName(final MethodMatching matching) {
        return matching.getModel().getMethod().getSimpleName().toString();
    }
}
