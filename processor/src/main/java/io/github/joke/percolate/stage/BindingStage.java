package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.ArrayList;
import java.util.Collections;
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
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

public final class BindingStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    BindingStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.propertyStrategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
                .forEach(propertyStrategies::add);
    }

    public void execute(MethodRegistry registry) {
        // Copy to avoid ConcurrentModificationException: buildMethodGraph mutates the registry
        new ArrayList<>(registry.entries().values())
                .stream()
                        .map(RegistryEntry::getSignature)
                        .filter(sig -> sig != null && sig.isAbstract())
                        .forEach(sig -> buildMethodGraph(sig, registry));
    }

    private void buildMethodGraph(MethodDefinition method, MethodRegistry registry) {
        DirectedWeightedMultigraph<MappingNode, FlowEdge> graph = new DirectedWeightedMultigraph<>(FlowEdge.class);

        List<SourceNode> sourceNodes = method.getParameters().stream()
                .map(param -> {
                    SourceNode node = new SourceNode(param.getName(), param.getType());
                    graph.addVertex(node);
                    return node;
                })
                .collect(toList());

        List<MapDirective> expanded = expandDirectives(method.getDirectives(), sourceNodes);
        Set<String> alreadyMapped =
                expanded.stream().map(MapDirective::getTarget).collect(toSet());
        List<MapDirective> sameNameDirectives = generateSameNameDirectives(sourceNodes, alreadyMapped);

        Stream.concat(expanded.stream(), sameNameDirectives.stream())
                .forEach(directive -> processDirective(graph, directive, method, sourceNodes));

        registry.lookup(method)
                .ifPresent(existing -> registry.register(
                        method, new RegistryEntry(existing.getSignature(), new AsUnmodifiableGraph<>(graph))));
    }

    /**
     * Expands wildcard directives (source ending in ".*") into per-property directives.
     * NOTE: ResolveStage currently runs before BindingStage and performs this expansion too.
     * This method becomes the sole expansion point once ResolveStage is removed in Task 10.
     */
    private List<MapDirective> expandDirectives(List<MapDirective> directives, List<SourceNode> sourceNodes) {
        List<MapDirective> expanded = new ArrayList<>();
        for (MapDirective directive : directives) {
            if (directive.getSource().endsWith(".*")) {
                expanded.addAll(expandWildcard(directive, sourceNodes));
            } else {
                expanded.add(directive);
            }
        }
        return expanded;
    }

    private List<MapDirective> expandWildcard(MapDirective directive, List<SourceNode> sourceNodes) {
        String sourcePath = directive.getSource();
        String prefix = sourcePath.substring(0, sourcePath.length() - 2);

        @Nullable TypeMirror paramType = resolvePathType(prefix, sourceNodes);
        if (paramType == null) {
            return Collections.emptyList();
        }
        @Nullable TypeElement typeElement = asTypeElement(paramType);
        if (typeElement == null) {
            return Collections.emptyList();
        }

        return discoverProperties(typeElement).stream()
                .map(prop -> new MapDirective(prop.getName(), prefix + "." + prop.getName()))
                .collect(toList());
    }

    private @Nullable TypeMirror resolvePathType(String paramName, List<SourceNode> sourceNodes) {
        return sourceNodes.stream()
                .filter(n -> n.getParamName().equals(paramName))
                .findFirst()
                .map(SourceNode::getType)
                .orElse(null);
    }

    private List<MapDirective> generateSameNameDirectives(List<SourceNode> sourceNodes, Set<String> alreadyMapped) {
        if (sourceNodes.size() != 1) {
            return Collections.emptyList();
        }
        SourceNode sourceNode = sourceNodes.get(0);
        @Nullable TypeElement sourceType = asTypeElement(sourceNode.getType());
        if (sourceType == null) {
            return Collections.emptyList();
        }
        return discoverProperties(sourceType).stream()
                .filter(prop -> !alreadyMapped.contains(prop.getName()))
                .map(prop -> new MapDirective(prop.getName(), prop.getName()))
                .collect(toList());
    }

    private void processDirective(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            MapDirective directive,
            MethodDefinition method,
            List<SourceNode> sourceNodes) {

        String target = directive.getTarget();
        if (target.equals(".")) {
            return; // "." targets are expanded by expandDirectives before processDirective is called
        }

        String sourcePath = directive.getSource();
        @SuppressWarnings("StringSplitter")
        String[] segments = sourcePath.split("\\.");

        EntryPoint entry = resolveEntryPoint(sourceNodes, segments);
        if (entry == null) {
            return;
        }

        MappingNode terminal = walkPropertyChain(graph, entry.getStartNode(), entry.getStartIndex(), segments);
        if (terminal == null) {
            return;
        }

        TypeMirror terminalType = getNodeType(terminal);
        TargetSlotPlaceholder slot = new TargetSlotPlaceholder(method.getReturnType(), target);
        graph.addVertex(slot);
        graph.addEdge(terminal, slot, FlowEdge.forSlot(terminalType, method.getReturnType(), target));
    }

    private @Nullable EntryPoint resolveEntryPoint(List<SourceNode> sourceNodes, String[] segments) {
        if (sourceNodes.isEmpty()) {
            return null;
        }
        if (sourceNodes.size() > 1) {
            return sourceNodes.stream()
                    .filter(n -> n.getParamName().equals(segments[0]))
                    .findFirst()
                    .map(n -> new EntryPoint(n, 1))
                    .orElse(null);
        }
        SourceNode only = sourceNodes.get(0);
        int startIndex = (segments.length > 1 && segments[0].equals(only.getParamName())) ? 1 : 0;
        return new EntryPoint(only, startIndex);
    }

    private @Nullable MappingNode walkPropertyChain(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            SourceNode startNode,
            int startIndex,
            String[] segments) {

        MappingNode current = startNode;
        TypeMirror currentType = startNode.getType();
        for (int i = startIndex; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.equals("*")) {
                return null; // ".*" sources are expanded before walkPropertyChain is called; defensive guard
            }
            @Nullable TypeElement typeElement = asTypeElement(currentType);
            if (typeElement == null) {
                return null;
            }
            Optional<Property> prop = discoverProperties(typeElement).stream()
                    .filter(p -> p.getName().equals(segment))
                    .findFirst();
            if (!prop.isPresent()) {
                return null;
            }
            Property property = prop.get();
            PropertyAccessNode propNode =
                    new PropertyAccessNode(segment, currentType, property.getType(), property.getAccessor());
            graph.addVertex(propNode);
            graph.addEdge(current, propNode, FlowEdge.of(currentType, property.getType()));
            current = propNode;
            currentType = property.getType();
        }
        return current;
    }

    private TypeMirror getNodeType(MappingNode node) {
        if (node instanceof SourceNode) {
            return ((SourceNode) node).getType();
        }
        return ((PropertyAccessNode) node).getOutType();
    }

    private Set<Property> discoverProperties(TypeElement type) {
        return propertyStrategies.stream()
                .flatMap(s -> s.discoverProperties(type, processingEnv).stream())
                .collect(toSet());
    }

    private @Nullable TypeElement asTypeElement(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) type).asElement();
        }
        return null;
    }

    private static final class EntryPoint {
        private final SourceNode startNode;
        private final int startIndex;

        EntryPoint(SourceNode startNode, int startIndex) {
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
