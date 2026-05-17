package io.github.joke.percolate.processor.stages.dump;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DumpGraph implements Stage {

    private final Filer filer;
    private final Diagnostics diagnostics;
    private final ProcessorOptions processorOptions;
    private final DotRenderer dotRenderer;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        apply(graph, ctx.getMapperType());
    }

    void apply(final MapperGraph graph, final TypeElement mapperType) {
        if (!processorOptions.isDebugGraphs()) {
            return;
        }
        if (graph.nodeCount() == 0 && graph.edgeCount() == 0) {
            return;
        }

        final var fqn = mapperType.getQualifiedName().toString();
        final var fileName = fqn + ".seed.dot";
        final var dotOutput = dotRenderer.render(graph, mapperType);

        try {
            final var resource = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", fileName, mapperType);
            try (var os = resource.openOutputStream();
                    var writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(dotOutput);
                writer.flush();
            }
        } catch (final IOException e) {
            diagnostics.warning(mapperType, "Failed to write debug graph: " + e.getMessage());
        }
    }
}
