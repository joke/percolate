package io.github.joke.percolate.stage;

import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.IntStream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jetbrains.annotations.Unmodifiable;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jspecify.annotations.Nullable;

public final class WiringStage {

    private final ProcessingEnvironment processingEnv;
    private final List<ObjectCreationStrategy> creationStrategies;
    private final List<ConversionProvider> conversionProviders;

    @Inject
    WiringStage(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.creationStrategies = loadCreationStrategies();
        this.conversionProviders = loadSortedConversionProviders();
    }

    private static List<ObjectCreationStrategy> loadCreationStrategies() {
        return ServiceLoader.load(ObjectCreationStrategy.class, WiringStage.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .collect(toUnmodifiableList());
    }

    private static List<ConversionProvider> loadSortedConversionProviders() {
        return ServiceLoader.load(ConversionProvider.class, WiringStage.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(comparingInt(ConversionProvider::priority))
                .collect(toUnmodifiableList());
    }

    public void execute(final MethodRegistry registry) {
        List.copyOf(registry.entries().values()).stream()
                .filter(entry -> !entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null)
                .forEach(entry -> wireMethod(entry, registry, conversionProviders));
    }

    private void wireMethod(
            final RegistryEntry entry, final MethodRegistry registry, final List<ConversionProvider> providers) {
        final var signature = requireNonNull(entry.getSignature());
        final var bindingGraph = requireNonNull(entry.getGraph());
        // Temporarily remove the current method from the registry so providers cannot
        // self-reference it (e.g. MapperMethodProvider resolving map(List<A>) via map itself).
        registry.register(signature, new RegistryEntry(null, null));
        final var wiredGraph = buildWiredGraph(bindingGraph, signature.getReturnType(), registry, providers);
        stabilizeGraph(wiredGraph, registry, providers);
        registry.register(signature, new RegistryEntry(signature, new AsUnmodifiableGraph<>(wiredGraph)));
    }

    private DirectedWeightedMultigraph<MappingNode, FlowEdge> buildWiredGraph(
            final Graph<MappingNode, FlowEdge> bindingGraph,
            final TypeMirror returnType,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var wiredGraph = new DirectedWeightedMultigraph<MappingNode, FlowEdge>(FlowEdge.class);
        final var nodeMap = new LinkedHashMap<MappingNode, MappingNode>();

        final var iterator = new TopologicalOrderIterator<>(bindingGraph);
        iterator.forEachRemaining(bindingNode -> {
            addWiredNode(bindingNode, returnType, wiredGraph, nodeMap);
            wireIncomingEdges(bindingNode, bindingGraph, wiredGraph, nodeMap, registry, providers);
        });
        return wiredGraph;
    }

    private void addWiredNode(
            final MappingNode bindingNode,
            final TypeMirror returnType,
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final Map<MappingNode, MappingNode> nodeMap) {
        final var wiredNode = substituteNode(bindingNode, returnType);
        wiredGraph.addVertex(wiredNode);
        nodeMap.put(bindingNode, wiredNode);
    }

    private void wireIncomingEdges(
            final MappingNode bindingNode,
            final Graph<MappingNode, FlowEdge> bindingGraph,
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final Map<MappingNode, MappingNode> nodeMap,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var wiredNode = requireNonNull(nodeMap.get(bindingNode));
        bindingGraph.incomingEdgesOf(bindingNode).forEach(edge -> {
            final var wiredSource = requireNonNull(nodeMap.get(bindingGraph.getEdgeSource(edge)));
            processEdge(wiredGraph, wiredSource, wiredNode, edge, registry, providers);
        });
    }

    private MappingNode substituteNode(final MappingNode bindingNode, final TypeMirror returnType) {
        if (!(bindingNode instanceof TargetSlotPlaceholder)) {
            return bindingNode;
        }
        final var typeElement = asTypeElement(returnType);
        if (typeElement == null) {
            return bindingNode;
        }
        final var descriptor = findCreationDescriptor(typeElement);
        if (descriptor == null) {
            return bindingNode;
        }
        return new ConstructorAssignmentNode(typeElement, descriptor);
    }

    private void processEdge(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final MappingNode wiredSource,
            final MappingNode wiredTarget,
            final FlowEdge bindingEdge,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var wiredEdge = adjustEdge(wiredTarget, bindingEdge);
        final var srcType = wiredEdge.getSourceType();
        final var tgtType = wiredEdge.getTargetType();
        if (typesCompatible(srcType, tgtType)) {
            wiredGraph.addEdge(wiredSource, wiredTarget, wiredEdge);
            return;
        }
        final var fragment = findFragment(srcType, tgtType, registry, providers);
        if (fragment.isPresent() && !fragment.get().isEmpty()) {
            spliceFragment(wiredGraph, wiredSource, wiredTarget, wiredEdge, fragment.get());
        }
        // If no fragment found, the edge is severed — dead-end detected by ValidateStage.
    }

    private FlowEdge adjustEdge(final MappingNode wiredTarget, final FlowEdge bindingEdge) {
        if (!(wiredTarget instanceof ConstructorAssignmentNode) || bindingEdge.getSlotName() == null) {
            return FlowEdge.of(bindingEdge.getSourceType(), bindingEdge.getTargetType());
        }
        final var slotName = bindingEdge.getSlotName();
        final var slotType = findSlotType((ConstructorAssignmentNode) wiredTarget, slotName, bindingEdge.getTargetType());
        return FlowEdge.forSlot(bindingEdge.getSourceType(), slotType, slotName);
    }

    private static TypeMirror findSlotType(
            final ConstructorAssignmentNode node, final String slotName, final TypeMirror fallback) {
        return node.getDescriptor().getParameters().stream()
                .filter(parameter -> parameter.getName().equals(slotName))
                .findFirst()
                .map(Property::getType)
                .orElse(fallback);
    }

    private static void spliceFragment(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final MappingNode source,
            final MappingNode target,
            final FlowEdge edge,
            final ConversionFragment fragment) {
        final var nodes = fragment.getNodes();
        wireConversionChain(wiredGraph, source, edge, nodes);
        reconnectToTarget(wiredGraph, target, edge, nodes);
    }

    private static void wireConversionChain(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final MappingNode source,
            final FlowEdge edge,
            final List<MappingNode> nodes) {
        IntStream.range(0, nodes.size()).forEach(index -> {
            final var conversionNode = nodes.get(index);
            wiredGraph.addVertex(conversionNode);
            final var predecessorNode = index == 0 ? source : nodes.get(index - 1);
            final var predecessorType = index == 0 ? edge.getSourceType() : outTypeOf(nodes.get(index - 1));
            wiredGraph.addEdge(predecessorNode, conversionNode, FlowEdge.of(predecessorType, inTypeOf(conversionNode)));
        });
    }

    private static void reconnectToTarget(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            final MappingNode target,
            final FlowEdge edge,
            final List<MappingNode> nodes) {
        final var lastNode = nodes.get(nodes.size() - 1);
        final var reconnect = edge.getSlotName() != null
                ? FlowEdge.forSlot(outTypeOf(lastNode), edge.getTargetType(), edge.getSlotName())
                : FlowEdge.of(outTypeOf(lastNode), edge.getTargetType());
        wiredGraph.addEdge(lastNode, target, reconnect);
    }

    private Optional<ConversionFragment> findFragment(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        return providers.stream()
                .filter(provider -> provider.canHandle(source, target, registry, processingEnv))
                .findFirst()
                .map(provider -> provider.provide(source, target, registry, processingEnv));
    }

    private boolean typesCompatible(final TypeMirror source, final TypeMirror target) {
        return processingEnv.getTypeUtils().isSameType(source, target);
    }

    private @Nullable CreationDescriptor findCreationDescriptor(final TypeElement type) {
        return creationStrategies.stream()
                .filter(strategy -> strategy.canCreate(type, processingEnv))
                .findFirst()
                .map(strategy -> strategy.describe(type, processingEnv))
                .orElse(null);
    }

    private static @Nullable TypeElement asTypeElement(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        return (TypeElement) ((DeclaredType) type).asElement();
    }

    private void stabilizeGraph(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var unused = IntStream.range(0, 10).allMatch(iteration -> expandIncompatibleEdges(graph, registry, providers));
    }

    @Unmodifiable
    private List<FlowEdge> findIncompatibleEdges(final Graph<MappingNode, FlowEdge> graph) {
        return graph.edgeSet().stream()
                .filter(edge -> !typesCompatible(edge.getSourceType(), edge.getTargetType()))
                .collect(toUnmodifiableList());
    }

    private boolean expandIncompatibleEdges(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var incompatible = findIncompatibleEdges(graph);
        if (incompatible.isEmpty()) {
            return false;
        }
        incompatible.forEach(edge -> expandEdge(graph, edge, registry, providers));
        return true;
    }

    private void expandEdge(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final FlowEdge edge,
            final MethodRegistry registry,
            final List<ConversionProvider> providers) {
        final var source = graph.getEdgeSource(edge);
        final var target = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);
        final var fragment = findFragment(edge.getSourceType(), edge.getTargetType(), registry, providers);
        if (fragment.isPresent() && !fragment.get().isEmpty()) {
            spliceFragment(graph, source, target, edge, fragment.get());
        }
        // If no fragment found, the edge is severed — the source node becomes a dead-end,
        // which ValidateStage will detect via forward/backward reachability analysis.
    }

    private static TypeMirror outTypeOf(final MappingNode node) {
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

    private static TypeMirror inTypeOf(final MappingNode node) {
        if (node instanceof SourceNode) return ((SourceNode) node).getType();
        if (node instanceof PropertyAccessNode) return ((PropertyAccessNode) node).getInType();
        if (node instanceof CollectionIterationNode) return ((CollectionIterationNode) node).getCollectionType();
        if (node instanceof CollectionCollectNode) return ((CollectionCollectNode) node).getElementType();
        if (node instanceof OptionalWrapNode) return ((OptionalWrapNode) node).getElementType();
        if (node instanceof OptionalUnwrapNode) return ((OptionalUnwrapNode) node).getOptionalType();
        if (node instanceof BoxingNode) return ((BoxingNode) node).getInType();
        if (node instanceof UnboxingNode) return ((UnboxingNode) node).getInType();
        if (node instanceof MethodCallNode) return ((MethodCallNode) node).getInType();
        throw new IllegalArgumentException(
                "Unknown node type: " + node.getClass().getSimpleName());
    }
}
