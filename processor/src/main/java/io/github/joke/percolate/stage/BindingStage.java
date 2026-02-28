package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.FlowEdge;
import io.github.joke.percolate.graph.node.MappingNode;
import io.github.joke.percolate.graph.node.PropertyAccessNode;
import io.github.joke.percolate.graph.node.SourceNode;
import io.github.joke.percolate.graph.node.TargetSlotPlaceholder;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class BindingStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;

    @Inject
    BindingStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.propertyStrategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
                .forEach(propertyStrategies::add);
    }

    public MethodRegistry execute(ParseResult parseResult) {
        parseResult
                .getMappers()
                .forEach(mapper ->
                        buildMapperGraphs(mapper, parseResult.getRegistries().get(mapper.getElement())));
        return parseResult.getRegistries().values().stream().reduce(new MethodRegistry(), this::mergeRegistries);
    }

    private void buildMapperGraphs(MapperDefinition mapper, @Nullable MethodRegistry registry) {
        if (registry == null) {
            return;
        }
        mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .forEach(method -> buildMethodGraph(method, registry));
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

        method.getDirectives().forEach(directive -> processDirective(graph, directive, method, sourceNodes));

        String inKey = buildInKey(method);
        Optional<RegistryEntry> existing =
                registry.lookup(inKey, method.getReturnType().toString());
        if (existing.isPresent()) {
            registry.register(
                    inKey,
                    method.getReturnType().toString(),
                    new RegistryEntry(existing.get().getSignature(), graph));
        }
    }

    private String buildInKey(MethodDefinition method) {
        if (method.getParameters().size() == 1) {
            return method.getParameters().get(0).getType().toString();
        }
        return method.getParameters().stream().map(p -> p.getType().toString()).reduce("(", (a, b) -> a + "," + b)
                + ")";
    }

    private void processDirective(
            DirectedWeightedMultigraph<MappingNode, FlowEdge> graph,
            MapDirective directive,
            MethodDefinition method,
            List<SourceNode> sourceNodes) {

        String sourcePath = directive.getSource();
        @SuppressWarnings("StringSplitter")
        String[] segments = sourcePath.split("\\.");

        SourceNode startNode;
        int startIndex;
        if (sourceNodes.size() > 1) {
            String firstName = segments[0];
            Optional<SourceNode> match = sourceNodes.stream()
                    .filter(n -> n.getParamName().equals(firstName))
                    .findFirst();
            if (!match.isPresent()) {
                return;
            }
            startNode = match.get();
            startIndex = 1;
        } else {
            SourceNode only = sourceNodes.get(0);
            if (segments.length > 1 && segments[0].equals(only.getParamName())) {
                startNode = only;
                startIndex = 1;
            } else {
                startNode = only;
                startIndex = 0;
            }
        }

        MappingNode current = startNode;
        TypeMirror currentType = startNode.getType();
        for (int i = startIndex; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.equals("*")) {
                break; // wildcard — handled in Task 6
            }
            @Nullable TypeElement typeElement = asTypeElement(currentType);
            if (typeElement == null) {
                return;
            }
            Set<Property> properties = discoverProperties(typeElement);
            Optional<Property> prop =
                    properties.stream().filter(p -> p.getName().equals(segment)).findFirst();
            if (!prop.isPresent()) {
                return;
            }
            Property property = prop.get();
            PropertyAccessNode propNode =
                    new PropertyAccessNode(segment, currentType, property.getType(), property.getAccessor());
            graph.addVertex(propNode);
            graph.addEdge(current, propNode, FlowEdge.of(currentType, property.getType()));
            current = propNode;
            currentType = property.getType();
        }

        String target = directive.getTarget();
        if (target.equals(".")) {
            return; // wildcard target — handled in Task 6
        }
        if (asTypeElement(method.getReturnType()) == null) {
            return;
        }
        TargetSlotPlaceholder slot = new TargetSlotPlaceholder(method.getReturnType(), target);
        graph.addVertex(slot);
        graph.addEdge(current, slot, FlowEdge.forSlot(currentType, method.getReturnType(), target));
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

    private MethodRegistry mergeRegistries(MethodRegistry a, MethodRegistry b) {
        b.entries().forEach((pair, entry) -> a.register(pair.getInTypeName(), pair.getOutTypeName(), entry));
        return a;
    }
}
