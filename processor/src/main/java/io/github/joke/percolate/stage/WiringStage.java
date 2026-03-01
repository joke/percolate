package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.concat;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.BoxingNode;
import io.github.joke.percolate.graph.node.CollectionCollectNode;
import io.github.joke.percolate.graph.node.CollectionIterationNode;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.graph.node.OptionalUnwrapNode;
import io.github.joke.percolate.graph.node.OptionalWrapNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.graph.node.UnboxingNode;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import io.github.joke.percolate.spi.impl.MapperMethodProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
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
    private final List<ConversionProvider> conversionProviders;

    @Inject
    WiringStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.creationStrategies = new ArrayList<>();
        this.conversionProviders = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
                .forEach(creationStrategies::add);
        ServiceLoader.load(ConversionProvider.class, getClass().getClassLoader())
                .forEach(conversionProviders::add);
    }

    public MethodRegistry execute(MethodRegistry registry) {
        List<ConversionProvider> providers = buildProviders(registry);
        registry.entries().forEach((pair, entry) -> {
            if (!entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null) {
                wireGraph(entry.getGraph(), entry.getSignature().getReturnType(), registry, providers);
            }
        });
        return registry;
    }

    private void wireGraph(
            Graph<MappingNode, FlowEdge> graph,
            TypeMirror returnType,
            MethodRegistry registry,
            List<ConversionProvider> providers) {
        resolveCreationStrategy(graph, returnType);
        insertConversions(graph, registry, providers);
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
        TypeMirror slotType = findSlotType(assignmentNode, slotName, returnType);
        new ArrayList<>(graph.incomingEdgesOf(placeholder)).forEach(edge -> {
            MappingNode source = graph.getEdgeSource(edge);
            graph.addEdge(source, assignmentNode, FlowEdge.forSlot(edge.getSourceType(), slotType, slotName));
        });
    }

    private static TypeMirror findSlotType(ConstructorAssignmentNode node, String slotName, TypeMirror fallback) {
        return node.getDescriptor().getParameters().stream()
                .filter(p -> p.getName().equals(slotName))
                .findFirst()
                .map(Property::getType)
                .orElse(fallback);
    }

    private void insertConversions(
            Graph<MappingNode, FlowEdge> graph,
            MethodRegistry registry,
            List<ConversionProvider> providers) {
        List<FlowEdge> edgesToCheck = new ArrayList<>(graph.edgeSet());
        for (FlowEdge edge : edgesToCheck) {
            if (!graph.containsEdge(edge)) continue;
            TypeMirror sourceType = edge.getSourceType();
            TypeMirror targetType = edge.getTargetType();
            if (typesCompatible(sourceType, targetType)) continue;
            findFragment(sourceType, targetType, registry, providers).ifPresent(fragment -> {
                if (!fragment.isEmpty()) {
                    spliceFragment(graph, edge, fragment);
                }
            });
        }
    }

    private boolean typesCompatible(TypeMirror source, TypeMirror target) {
        return processingEnv
                .getTypeUtils()
                .isSameType(
                        processingEnv.getTypeUtils().erasure(source),
                        processingEnv.getTypeUtils().erasure(target));
    }

    private Optional<ConversionFragment> findFragment(
            TypeMirror source,
            TypeMirror target,
            MethodRegistry registry,
            List<ConversionProvider> providers) {
        return providers.stream()
                .filter(p -> p.canHandle(source, target, processingEnv))
                .findFirst()
                .map(p -> p.provide(source, target, registry, processingEnv));
    }

    private List<ConversionProvider> buildProviders(MethodRegistry registry) {
        return concat(Stream.of(new MapperMethodProvider(registry)), conversionProviders.stream())
                .collect(toUnmodifiableList());
    }

    private void spliceFragment(Graph<MappingNode, FlowEdge> graph, FlowEdge edge, ConversionFragment fragment) {
        MappingNode edgeSource = graph.getEdgeSource(edge);
        MappingNode edgeTarget = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);

        List<MappingNode> nodes = fragment.getNodes();
        MappingNode prev = edgeSource;
        TypeMirror prevType = edge.getSourceType();
        for (MappingNode node : nodes) {
            graph.addVertex(node);
            TypeMirror nodeOutType = outTypeOf(node);
            graph.addEdge(prev, node, FlowEdge.of(prevType, nodeOutType));
            prev = node;
            prevType = nodeOutType;
        }
        FlowEdge reconnect = edge.getSlotName() != null
                ? FlowEdge.forSlot(prevType, edge.getTargetType(), edge.getSlotName())
                : FlowEdge.of(prevType, edge.getTargetType());
        graph.addEdge(prev, edgeTarget, reconnect);
    }

    private static TypeMirror outTypeOf(MappingNode node) {
        if (node instanceof SourceNode) return ((SourceNode) node).getType();
        if (node instanceof PropertyAccessNode) return ((PropertyAccessNode) node).getOutType();
        if (node instanceof CollectionIterationNode) return ((CollectionIterationNode) node).getElementType();
        if (node instanceof CollectionCollectNode) return ((CollectionCollectNode) node).getTargetCollectionType();
        if (node instanceof OptionalWrapNode) return ((OptionalWrapNode) node).getOptionalType();
        if (node instanceof OptionalUnwrapNode) return ((OptionalUnwrapNode) node).getElementType();
        if (node instanceof BoxingNode) return ((BoxingNode) node).getOutType();
        if (node instanceof UnboxingNode) return ((UnboxingNode) node).getOutType();
        if (node instanceof MethodCallNode) return ((MethodCallNode) node).getOutType();
        throw new IllegalArgumentException(
                "Unknown node type: " + node.getClass().getSimpleName());
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
