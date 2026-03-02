package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.concat;

import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.ConstructorAssignmentNode;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import io.github.joke.percolate.spi.impl.MapperMethodProvider;
import io.github.joke.percolate.graph.node.BoxingNode;
import io.github.joke.percolate.graph.node.CollectionCollectNode;
import io.github.joke.percolate.graph.node.CollectionIterationNode;
import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.graph.node.OptionalUnwrapNode;
import io.github.joke.percolate.graph.node.OptionalWrapNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.UnboxingNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
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
    WiringStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.creationStrategies = new ArrayList<>();
        this.conversionProviders = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
                .forEach(creationStrategies::add);
        ServiceLoader.load(ConversionProvider.class, getClass().getClassLoader())
                .forEach(conversionProviders::add);
    }

    public void execute(MethodRegistry registry) {
        List<ConversionProvider> providers = buildProviders(registry);
        new ArrayList<>(registry.entries().entrySet()).forEach(e -> {
            RegistryEntry entry = e.getValue();
            if (!entry.isOpaque() && entry.getGraph() != null && entry.getSignature() != null) {
                wireMethod(entry, registry, providers);
            }
        });
    }

    private void wireMethod(RegistryEntry entry, MethodRegistry registry, List<ConversionProvider> providers) {
        Graph<MappingNode, FlowEdge> bindingGraph = Objects.requireNonNull(entry.getGraph());
        MethodDefinition signature = Objects.requireNonNull(entry.getSignature());
        DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph =
                new DirectedWeightedMultigraph<>(FlowEdge.class);
        Map<MappingNode, MappingNode> nodeMap = new LinkedHashMap<>();
        TypeMirror returnType = signature.getReturnType();

        TopologicalOrderIterator<MappingNode, FlowEdge> iter =
                new TopologicalOrderIterator<>(bindingGraph);
        while (iter.hasNext()) {
            MappingNode bindingNode = iter.next();
            MappingNode wiredNode = substituteNode(bindingNode, returnType);
            wiredGraph.addVertex(wiredNode);
            nodeMap.put(bindingNode, wiredNode);

            bindingGraph.incomingEdgesOf(bindingNode).forEach(edge -> {
                MappingNode wiredSource = Objects.requireNonNull(nodeMap.get(bindingGraph.getEdgeSource(edge)));
                processEdge(wiredGraph, wiredSource, wiredNode, edge, registry, providers);
            });
        }

        registry.register(
                signature,
                new RegistryEntry(signature, new AsUnmodifiableGraph<>(wiredGraph)));
    }

    private MappingNode substituteNode(MappingNode bindingNode, TypeMirror returnType) {
        if (!(bindingNode instanceof TargetSlotPlaceholder)) {
            return bindingNode;
        }
        @Nullable TypeElement typeElement = asTypeElement(returnType);
        if (typeElement == null) {
            return bindingNode;
        }
        @Nullable CreationDescriptor descriptor = findCreationDescriptor(typeElement);
        if (descriptor == null) {
            return bindingNode;
        }
        return new ConstructorAssignmentNode(typeElement, descriptor);
    }

    private void processEdge(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            MappingNode wiredSource,
            MappingNode wiredTarget,
            FlowEdge bindingEdge,
            MethodRegistry registry,
            List<ConversionProvider> providers) {
        FlowEdge wiredEdge = adjustEdge(wiredTarget, bindingEdge);
        TypeMirror srcType = wiredEdge.getSourceType();
        TypeMirror tgtType = wiredEdge.getTargetType();
        if (typesCompatible(srcType, tgtType)) {
            wiredGraph.addEdge(wiredSource, wiredTarget, wiredEdge);
            return;
        }
        Optional<ConversionFragment> fragment = findFragment(srcType, tgtType, registry, providers);
        if (fragment.isPresent() && !fragment.get().isEmpty()) {
            spliceFragment(wiredGraph, wiredSource, wiredTarget, wiredEdge, fragment.get());
        } else {
            wiredGraph.addEdge(wiredSource, wiredTarget, wiredEdge);
        }
    }

    private FlowEdge adjustEdge(MappingNode wiredTarget, FlowEdge bindingEdge) {
        if (!(wiredTarget instanceof ConstructorAssignmentNode) || bindingEdge.getSlotName() == null) {
            return FlowEdge.of(bindingEdge.getSourceType(), bindingEdge.getTargetType());
        }
        String slotName = bindingEdge.getSlotName();
        TypeMirror slotType = findSlotType(
                (ConstructorAssignmentNode) wiredTarget, slotName, bindingEdge.getTargetType());
        return FlowEdge.forSlot(bindingEdge.getSourceType(), slotType, slotName);
    }

    private static TypeMirror findSlotType(
            ConstructorAssignmentNode node, String slotName, TypeMirror fallback) {
        return node.getDescriptor().getParameters().stream()
                .filter(p -> p.getName().equals(slotName))
                .findFirst()
                .map(Property::getType)
                .orElse(fallback);
    }

    private void spliceFragment(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> wiredGraph,
            MappingNode source,
            MappingNode target,
            FlowEdge edge,
            ConversionFragment fragment) {
        List<MappingNode> nodes = fragment.getNodes();
        MappingNode prev = source;
        TypeMirror prevType = edge.getSourceType();
        for (MappingNode node : nodes) {
            wiredGraph.addVertex(node);
            TypeMirror nodeOutType = outTypeOf(node);
            wiredGraph.addEdge(prev, node, FlowEdge.of(prevType, nodeOutType));
            prev = node;
            prevType = nodeOutType;
        }
        FlowEdge reconnect = edge.getSlotName() != null
                ? FlowEdge.forSlot(prevType, edge.getTargetType(), edge.getSlotName())
                : FlowEdge.of(prevType, edge.getTargetType());
        wiredGraph.addEdge(prev, target, reconnect);
    }

    private List<ConversionProvider> buildProviders(MethodRegistry registry) {
        return concat(Stream.of(new MapperMethodProvider(registry)), conversionProviders.stream())
                .collect(toUnmodifiableList());
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

    private boolean typesCompatible(TypeMirror source, TypeMirror target) {
        return processingEnv.getTypeUtils().isSameType(
                processingEnv.getTypeUtils().erasure(source),
                processingEnv.getTypeUtils().erasure(target));
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
        throw new IllegalArgumentException("Unknown node type: " + node.getClass().getSimpleName());
    }
}
