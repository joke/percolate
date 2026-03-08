package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

public final class BindingStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    BindingStage(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.propertyStrategies = loadPropertyStrategies();
    }

    private static List<PropertyDiscoveryStrategy> loadPropertyStrategies() {
        return ServiceLoader.load(PropertyDiscoveryStrategy.class, BindingStage.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .collect(toUnmodifiableList());
    }

    public void execute(final MethodRegistry registry) {
        // Copy to avoid ConcurrentModificationException: buildMethodGraph mutates the registry
        List.copyOf(registry.entries().values()).stream()
                .map(RegistryEntry::getSignature)
                .filter(signature -> signature != null && signature.isAbstract())
                .forEach(signature -> buildMethodGraph(signature, registry));
    }

    private void buildMethodGraph(final MethodDefinition method, final MethodRegistry registry) {
        final var graph = new DirectedWeightedMultigraph<MappingNode, FlowEdge>(FlowEdge.class);

        final var target = new TargetSlotPlaceholder(method.getReturnType());
        graph.addVertex(target);

        final var sourceNodes = method.getParameters().stream()
                .map(param -> {
                    final var node = new SourceNode(param.getName(), param.getType());
                    graph.addVertex(node);
                    return node;
                })
                .collect(toUnmodifiableList());

        final var expanded = expandDirectives(method.getDirectives(), sourceNodes);
        final var alreadyMapped = expanded.stream()
                .flatMap(directive -> Stream.of(directive.getTarget(), sourceLeaf(directive.getSource())))
                .collect(toSet());
        final var sameNameDirectives = generateSameNameDirectives(sourceNodes, alreadyMapped, target.getTargetType());

        Stream.concat(expanded.stream(), sameNameDirectives.stream())
                .forEach(directive -> processDirective(graph, directive, target, sourceNodes));

        if (graph.incomingEdgesOf(target).isEmpty()) {
            sourceNodes.forEach(src -> graph.addEdge(src, target, FlowEdge.of(src.getType(), target.getTargetType())));
        }

        registry.lookup(method)
                .ifPresent(existing -> registry.register(
                        method, new RegistryEntry(existing.getSignature(), new AsUnmodifiableGraph<>(graph))));
    }

    /**
     * Expands wildcard directives (source ending in ".*") into per-property directives.
     * NOTE: ResolveStage currently runs before BindingStage and performs this expansion too.
     * This method becomes the sole expansion point once ResolveStage is removed in Task 10.
     */
    private List<MapDirective> expandDirectives(
            final List<MapDirective> directives, final List<SourceNode> sourceNodes) {
        return directives.stream()
                .flatMap(directive -> directive.getSource().endsWith(".*")
                        ? expandWildcard(directive, sourceNodes).stream()
                        : Stream.of(directive))
                .collect(toUnmodifiableList());
    }

    private List<MapDirective> expandWildcard(final MapDirective directive, final List<SourceNode> sourceNodes) {
        final var sourcePath = directive.getSource();
        final var prefix = sourcePath.substring(0, sourcePath.length() - 2);

        final var paramType = resolvePathType(prefix, sourceNodes);
        if (paramType == null) {
            return Collections.emptyList();
        }
        final var typeElement = asTypeElement(paramType);
        if (typeElement == null) {
            return Collections.emptyList();
        }

        return discoverProperties(typeElement).stream()
                .map(prop -> new MapDirective(prop.getName(), prefix + "." + prop.getName()))
                .collect(toUnmodifiableList());
    }

    private @Nullable TypeMirror resolvePathType(final String paramName, final List<SourceNode> sourceNodes) {
        return sourceNodes.stream()
                .filter(sourceNode -> sourceNode.getParamName().equals(paramName))
                .findFirst()
                .map(SourceNode::getType)
                .orElse(null);
    }

    private List<MapDirective> generateSameNameDirectives(
            final List<SourceNode> sourceNodes, final Set<String> alreadyMapped, final TypeMirror targetType) {
        if (sourceNodes.size() != 1) {
            return Collections.emptyList();
        }
        final var sourceNode = sourceNodes.get(0);
        if (sameErasure(sourceNode.getType(), targetType)) {
            return Collections.emptyList();
        }
        final var sourceType = asTypeElement(sourceNode.getType());
        if (sourceType == null) {
            return Collections.emptyList();
        }
        final var targetSlotNames = targetSlotNames(targetType);
        return discoverProperties(sourceType).stream()
                .filter(prop -> !alreadyMapped.contains(prop.getName()))
                .filter(prop -> targetSlotNames.isEmpty() || targetSlotNames.contains(prop.getName()))
                .map(prop -> new MapDirective(prop.getName(), prop.getName()))
                .collect(toUnmodifiableList());
    }

    private Set<String> targetSlotNames(final TypeMirror targetType) {
        final var targetElement = asTypeElement(targetType);
        if (targetElement == null) {
            return Collections.emptySet();
        }
        return discoverProperties(targetElement).stream().map(Property::getName).collect(toSet());
    }

    private boolean sameErasure(final TypeMirror a, final TypeMirror b) {
        final var types = processingEnv.getTypeUtils();
        return types.isSameType(types.erasure(a), types.erasure(b));
    }

    private void processDirective(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final MapDirective directive,
            final TargetSlotPlaceholder target,
            final List<SourceNode> sourceNodes) {

        final var slotName = directive.getTarget();
        if (slotName.equals(".")) {
            return; // "." targets are expanded by expandDirectives before processDirective is called
        }

        final var sourcePath = directive.getSource();
        @SuppressWarnings("StringSplitter")
        final var segments = sourcePath.split("\\.");

        final var entry = resolveEntryPoint(sourceNodes, segments);
        if (entry == null) {
            return;
        }

        final var terminal = walkPropertyChain(graph, entry.getStartNode(), entry.getStartIndex(), segments);
        if (terminal == null) {
            return;
        }

        final var terminalType = getNodeType(terminal);
        graph.addEdge(terminal, target, FlowEdge.forSlot(terminalType, target.getTargetType(), slotName));
    }

    private @Nullable EntryPoint resolveEntryPoint(final List<SourceNode> sourceNodes, final String[] segments) {
        if (sourceNodes.isEmpty()) {
            return null;
        }
        if (sourceNodes.size() > 1) {
            return sourceNodes.stream()
                    .filter(sourceNode -> sourceNode.getParamName().equals(segments[0]))
                    .findFirst()
                    .map(sourceNode -> new EntryPoint(sourceNode, 1))
                    .orElse(null);
        }
        final var only = sourceNodes.get(0);
        final var startIndex = (segments.length > 1 && segments[0].equals(only.getParamName())) ? 1 : 0;
        return new EntryPoint(only, startIndex);
    }

    private @Nullable MappingNode walkPropertyChain(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final SourceNode startNode,
            final int startIndex,
            final String[] segments) {
        return walkSegments(graph, startNode, startNode.getType(), startIndex, segments);
    }

    private @Nullable MappingNode walkSegments(
            final DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            final MappingNode current,
            final TypeMirror currentType,
            final int index,
            final String[] segments) {
        if (index >= segments.length) {
            return current;
        }
        final var segment = segments[index];
        if (segment.equals("*")) {
            return null; // ".*" sources are expanded before walkPropertyChain is called; defensive guard
        }
        final var typeElement = asTypeElement(currentType);
        if (typeElement == null) {
            return null;
        }
        final var prop = discoverProperties(typeElement).stream()
                .filter(property -> property.getName().equals(segment))
                .findFirst();
        if (prop.isEmpty()) {
            return null;
        }
        final var property = prop.get();
        final var propNode = new PropertyAccessNode(segment, currentType, property.getType(), property.getAccessor());
        graph.addVertex(propNode);
        graph.addEdge(current, propNode, FlowEdge.of(currentType, property.getType()));
        return walkSegments(graph, propNode, property.getType(), index + 1, segments);
    }

    private TypeMirror getNodeType(final MappingNode node) {
        if (node instanceof SourceNode) {
            return ((SourceNode) node).getType();
        }
        return ((PropertyAccessNode) node).getOutType();
    }

    private Set<Property> discoverProperties(final TypeElement type) {
        return propertyStrategies.stream()
                .flatMap(strategy -> strategy.discoverProperties(type, processingEnv).stream())
                .collect(toSet());
    }

    private static String sourceLeaf(final String source) {
        final var dot = source.lastIndexOf('.');
        return dot >= 0 ? source.substring(dot + 1) : source;
    }

    private @Nullable TypeElement asTypeElement(final TypeMirror type) {
        if (type instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) type).asElement();
        }
        return null;
    }

    private static final class EntryPoint {
        private final SourceNode startNode;
        private final int startIndex;

        EntryPoint(final SourceNode startNode, final int startIndex) {
            this.startNode = startNode;
            this.startIndex = startIndex;
        }

        SourceNode getStartNode() {
            return startNode;
        }

        int getStartIndex() {
            return startIndex;
        }
    }
}
