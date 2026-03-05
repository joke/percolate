package io.github.joke.percolate.processor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.jgrapht.nio.DefaultAttribute.createAttribute;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseMapperStage;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.RegistryEntry;
import io.github.joke.percolate.stage.ValidateStage;
import io.github.joke.percolate.stage.WiringStage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import org.jgrapht.Graph;
import org.jgrapht.nio.dot.DOTExporter;

@RoundScoped
public class Pipeline {

    private final ParseMapperStage parseMapperStage;
    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;
    private final ValidateStage validateStage;

    @Inject
    Pipeline(
            final ParseMapperStage parseMapperStage,
            final RegistrationStage registrationStage,
            final BindingStage bindingStage,
            final WiringStage wiringStage,
            final ValidateStage validateStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
        this.validateStage = validateStage;
    }

    public void process(final TypeElement typeElement) {
        final var mapper = parseMapperStage.execute(typeElement);
        final var registry = registrationStage.execute(mapper);
        bindingStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "binding");
        wiringStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "wiring");
        validateStage.execute(registry);
    }

    private static void exportDot(final String mapperName, final MethodRegistry registry, final String phase) {
        registry.entries().forEach((typePair, registryEntry) -> writeEntryDot(mapperName, phase, registryEntry));
    }

    private static void writeEntryDot(final String mapperName, final String phase, final RegistryEntry entry) {
        if (entry.isOpaque() || entry.getSignature() == null || entry.getGraph() == null) {
            return;
        }
        final var methodName = entry.getSignature().getName();
        final var path = buildDotPath(mapperName, phase, methodName);
        writeGraph(path, buildDotExporter(), entry.getGraph());
    }

    private static String buildDotPath(final String mapperName, final String phase, final String methodName) {
        return "/tmp/" + mapperName + "-" + methodName + "-" + phase + ".dot";
    }

    private static DOTExporter<MappingNode, FlowEdge> buildDotExporter() {
        final var exporter = new DOTExporter<MappingNode, FlowEdge>();
        exporter.setVertexIdProvider(node -> String.valueOf(System.identityHashCode(node)));
        exporter.setVertexAttributeProvider(node -> singletonMap("label", createAttribute(node.toString())));
        exporter.setEdgeAttributeProvider(edge -> singletonMap("label", createAttribute(edge.toString())));
        return exporter;
    }

    private static void writeGraph(
            final String path,
            final DOTExporter<MappingNode, FlowEdge> exporter,
            final Graph<MappingNode, FlowEdge> graph) {
        try (final var writer = Files.newBufferedWriter(Paths.get(path), UTF_8)) {
            exporter.exportGraph(graph, writer);
        } catch (IOException ignored) {
            // debug aid — processor must not fail if /tmp/ is unwritable
        }
    }
}
