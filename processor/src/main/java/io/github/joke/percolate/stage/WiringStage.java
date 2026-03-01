package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.Graph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class WiringStage {

    private final ProcessingEnvironment processingEnv;
    private final List<ObjectCreationStrategy> creationStrategies;

    @Inject
    WiringStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.creationStrategies = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
                .forEach(creationStrategies::add);
    }

    public MethodRegistry execute(MethodRegistry registry) {
        registry.entries().forEach((pair, entry) -> {
            if (!entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null) {
                wireGraph(entry.getGraph(), entry.getSignature().getReturnType());
            }
        });
        return registry;
    }

    private void wireGraph(Graph<MappingNode, FlowEdge> graph, TypeMirror returnType) {
        resolveCreationStrategy(graph, returnType);
        // Conversion insertion follows in Task 9
    }

    private void resolveCreationStrategy(Graph<MappingNode, FlowEdge> graph, TypeMirror returnType) {
        @Nullable TypeElement returnTypeElement = asTypeElement(returnType);
        if (returnTypeElement == null) return;

        @Nullable CreationDescriptor descriptor = findCreationDescriptor(returnTypeElement);
        if (descriptor == null) return;

        ConstructorAssignmentNode assignmentNode = new ConstructorAssignmentNode(returnTypeElement, descriptor);
        graph.addVertex(assignmentNode);
        replacePlaceholders(graph, assignmentNode, returnType);
    }

    private void replacePlaceholders(
            Graph<MappingNode, FlowEdge> graph, ConstructorAssignmentNode assignmentNode, TypeMirror returnType) {
        Set<MappingNode> placeholders = graph.vertexSet().stream()
                .filter(TargetSlotPlaceholder.class::isInstance)
                .collect(toSet());

        for (MappingNode placeholder : placeholders) {
            rewireIncomingEdges(graph, placeholder, assignmentNode, returnType);
            graph.removeVertex(placeholder);
        }
    }

    private void rewireIncomingEdges(
            Graph<MappingNode, FlowEdge> graph,
            MappingNode placeholder,
            ConstructorAssignmentNode assignmentNode,
            TypeMirror returnType) {
        String slotName = ((TargetSlotPlaceholder) placeholder).getSlotName();
        new ArrayList<>(graph.incomingEdgesOf(placeholder)).forEach(edge -> {
            MappingNode source = graph.getEdgeSource(edge);
            graph.addEdge(source, assignmentNode, FlowEdge.forSlot(edge.getSourceType(), returnType, slotName));
        });
    }

    private @Nullable CreationDescriptor findCreationDescriptor(TypeElement type) {
        return creationStrategies.stream()
                .filter(s -> s.canCreate(type, processingEnv))
                .findFirst()
                .map(s -> s.describe(type, processingEnv))
                .orElse(null);
    }

    private static @Nullable TypeElement asTypeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        return (TypeElement) ((DeclaredType) type).asElement();
    }
}
