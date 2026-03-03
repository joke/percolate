package io.github.joke.percolate.processor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.jgrapht.nio.DefaultAttribute.createAttribute;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseMapperStage;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.RegistryEntry;
import io.github.joke.percolate.stage.ValidateStage;
import io.github.joke.percolate.stage.WiringStage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
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
            ParseMapperStage parseMapperStage,
            RegistrationStage registrationStage,
            BindingStage bindingStage,
            WiringStage wiringStage,
            ValidateStage validateStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
        this.validateStage = validateStage;
    }

    public void process(TypeElement typeElement) {
        MapperDefinition mapper = parseMapperStage.execute(typeElement);
        MethodRegistry registry = registrationStage.execute(mapper);
        bindingStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "binding");
        wiringStage.execute(registry);
        exportDot(mapper.getSimpleName(), registry, "wiring");
        validateStage.execute(registry);
    }

    private static void exportDot(String mapperName, MethodRegistry registry, String phase) {
        registry.entries().forEach((typePair, entry) -> writeEntryDot(mapperName, phase, entry));
    }

    private static void writeEntryDot(String mapperName, String phase, RegistryEntry entry) {
        if (entry.isOpaque() || entry.getSignature() == null || entry.getGraph() == null) {
            return;
        }
        String methodName = Objects.requireNonNull(entry.getSignature()).getName();
        String path = "/tmp/" + mapperName + "-" + methodName + "-" + phase + ".dot";
        DOTExporter<MappingNode, FlowEdge> exporter = new DOTExporter<>();
        exporter.setVertexIdProvider(v -> String.valueOf(System.identityHashCode(v)));
        exporter.setVertexAttributeProvider(v -> singletonMap("label", createAttribute(v.toString())));
        exporter.setEdgeAttributeProvider(e -> singletonMap("label", createAttribute(e.toString())));
        try (Writer w = Files.newBufferedWriter(Paths.get(path), UTF_8)) {
            exporter.exportGraph(Objects.requireNonNull(entry.getGraph()), w);
        } catch (IOException ignored) {
            // debug aid — processor must not fail if /tmp/ is unwritable
        }
    }
}
