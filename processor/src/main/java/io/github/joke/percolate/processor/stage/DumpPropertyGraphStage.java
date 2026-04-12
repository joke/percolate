package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.WARNING;

import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.graph.AccessEdge;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.PackageElement;
import lombok.RequiredArgsConstructor;
import org.jgrapht.nio.DefaultAttribute;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpPropertyGraphStage {

    private final ProcessorOptions options;
    private final Filer filer;
    private final Messager messager;

    public void execute(final MappingGraph mappingGraph) {
        if (!options.isDebugGraphs()) {
            return;
        }
        final var mapperType = mappingGraph.getMapperType();
        final var packageElement = Objects.requireNonNull(
                (PackageElement) mapperType.getEnclosingElement(), "mapper must have enclosing package");
        final var packageName = packageElement.getQualifiedName().toString();
        final var mapperName = mapperType.getSimpleName().toString();

        for (final var entry : mappingGraph.getMethodGraphs().entrySet()) {
            final var method = entry.getKey();
            final var graph = entry.getValue();
            final var baseName = mapperName + "_" + methodName(method) + "_property";
            try {
                GraphExportSupport.writeGraph(
                        graph,
                        Object::toString,
                        edge -> {
                            if (edge instanceof AccessEdge) {
                                return Map.of("label", DefaultAttribute.createAttribute("access"));
                            }
                            if (edge instanceof MappingEdge) {
                                return Map.of("label", DefaultAttribute.createAttribute("mapping"));
                            }
                            return Map.of("label", DefaultAttribute.createAttribute(edge.toString()));
                        },
                        filer,
                        packageName,
                        baseName,
                        options.getDebugGraphsFormat());
            } catch (final IOException e) {
                messager.printMessage(
                        WARNING, "Failed to write property graph for " + baseName + ": " + e.getMessage());
            }
        }
    }

    private static String methodName(final MappingMethodModel method) {
        return method.getMethod().getSimpleName().toString();
    }
}
