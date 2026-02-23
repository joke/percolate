package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.ConstructorResultEdge;
import io.github.joke.percolate.graph.edge.GenericPlaceholderEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.edge.PropertyAccessEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.MethodNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
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
public class GraphBuildStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> propertyStrategies;
    private final List<ObjectCreationStrategy> creationStrategies;

    @Inject
    GraphBuildStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.propertyStrategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
                .forEach(propertyStrategies::add);
        this.creationStrategies = new ArrayList<>();
        ServiceLoader.load(ObjectCreationStrategy.class, getClass().getClassLoader())
                .forEach(creationStrategies::add);
    }

    public GraphResult execute(ResolveResult resolveResult) {
        DirectedWeightedMultigraph<GraphNode, GraphEdge> graph = new DirectedWeightedMultigraph<>(GraphEdge.class);

        resolveResult.getMappers().forEach(mapper -> buildMapperGraph(graph, mapper));

        return new GraphResult(graph, resolveResult.getMappers());
    }

    private void buildMapperGraph(DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, MapperDefinition mapper) {
        mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .forEach(method -> buildMethodGraph(graph, method, mapper));
    }

    private void buildMethodGraph(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, MethodDefinition method, MapperDefinition mapper) {
        MethodNode methodNode = new MethodNode(method);
        graph.addVertex(methodNode);

        // Create TypeNodes for each parameter
        List<TypeNode> paramNodes = method.getParameters().stream()
                .map(param -> {
                    TypeNode paramNode = new TypeNode(param.getType(), param.getName());
                    graph.addVertex(paramNode);
                    return paramNode;
                })
                .collect(toList());

        // Create TypeNode for return type
        TypeMirror returnType = method.getReturnType();
        TypeNode returnNode = new TypeNode(returnType, "return");
        graph.addVertex(returnNode);

        // Create ConstructorNode for return type
        @Nullable TypeElement returnTypeElement = asTypeElement(returnType);
        if (returnTypeElement == null) {
            return;
        }

        @Nullable CreationDescriptor descriptor = findCreationDescriptor(returnTypeElement);
        if (descriptor == null) {
            return;
        }

        ConstructorNode constructorNode = new ConstructorNode(returnTypeElement, descriptor);
        graph.addVertex(constructorNode);

        // Add ConstructorResultEdge from ConstructorNode to return TypeNode
        graph.addEdge(constructorNode, returnNode, new ConstructorResultEdge());

        // Process each MapDirective
        method.getDirectives()
                .forEach(directive ->
                        processDirective(graph, directive, method, paramNodes, constructorNode, descriptor, mapper));
    }

    private void processDirective(
            DirectedWeightedMultigraph<GraphNode, GraphEdge> graph,
            MapDirective directive,
            MethodDefinition method,
            List<TypeNode> paramNodes,
            ConstructorNode constructorNode,
            CreationDescriptor descriptor,
            MapperDefinition mapper) {
        // Parse source path
        String sourcePath = directive.getSource();
        @SuppressWarnings("StringSplitter")
        String[] sourceSegments = sourcePath.split("\\.");

        // Determine starting node and property chain
        GraphNode currentNode;
        int startIndex;

        String firstSegment = sourceSegments[0];
        Optional<TypeNode> matchingParam = paramNodes.stream()
                .filter(n -> n.getLabel().equals(firstSegment))
                .findFirst();

        if (matchingParam.isPresent()) {
            // First segment is a parameter name — skip it and start from that param node
            currentNode = matchingParam.get();
            startIndex = 1;
        } else if (method.getParameters().size() == 1) {
            // Single-param fallback: source path starts from the single parameter's type
            currentNode = paramNodes.get(0);
            startIndex = 0;
        } else {
            return;
        }

        // Walk the source chain
        for (int i = startIndex; i < sourceSegments.length; i++) {
            String segment = sourceSegments[i];
            @Nullable TypeMirror currentType = getNodeType(currentNode);
            if (currentType == null) {
                return;
            }

            @Nullable TypeElement currentTypeElement = asTypeElement(currentType);
            if (currentTypeElement == null) {
                return;
            }

            Set<Property> properties = discoverProperties(currentTypeElement);
            Optional<Property> matchingProp =
                    properties.stream().filter(p -> p.getName().equals(segment)).findFirst();

            if (!matchingProp.isPresent()) {
                return;
            }

            Property property = matchingProp.get();
            PropertyNode propertyNode = new PropertyNode(currentNode, property);
            graph.addVertex(propertyNode);
            graph.addEdge(currentNode, propertyNode, new PropertyAccessEdge(property));
            currentNode = propertyNode;
        }

        // Parse target path and find matching constructor parameter
        String targetPath = directive.getTarget();
        int paramIndex = findConstructorParamIndex(descriptor, targetPath);
        if (paramIndex < 0) {
            return;
        }

        // Check type compatibility
        @Nullable TypeMirror sourceType = getNodeType(currentNode);
        TypeMirror targetType = descriptor.getParameters().get(paramIndex).getType();

        if (sourceType != null && !typesMatch(sourceType, targetType)) {
            // Check for mapper method
            @Nullable MethodDefinition matchingMethod = findMapperMethod(mapper, sourceType, targetType);
            if (matchingMethod != null) {
                // Insert intermediate TypeNode with MethodCallEdge — handled by later stages
            } else {
                // Add GenericPlaceholderEdge for later expansion
                TypeNode sourceTypeNode = ensureTypeNode(graph, sourceType);
                TypeNode targetTypeNode = ensureTypeNode(graph, targetType);
                graph.addEdge(sourceTypeNode, targetTypeNode, new GenericPlaceholderEdge(sourceType, targetType));
            }
        }

        // Add ConstructorParamEdge
        graph.addEdge(
                currentNode,
                constructorNode,
                new ConstructorParamEdge(
                        descriptor.getParameters().get(paramIndex).getName(), paramIndex));
    }

    private @Nullable TypeMirror getNodeType(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    private int findConstructorParamIndex(CreationDescriptor descriptor, String targetPath) {
        List<Property> params = descriptor.getParameters();
        return range(0, params.size())
                .filter(i -> params.get(i).getName().equals(targetPath))
                .findFirst()
                .orElse(-1);
    }

    private boolean typesMatch(TypeMirror source, TypeMirror target) {
        return processingEnv
                .getTypeUtils()
                .isSameType(
                        processingEnv.getTypeUtils().erasure(source),
                        processingEnv.getTypeUtils().erasure(target));
    }

    private @Nullable MethodDefinition findMapperMethod(
            MapperDefinition mapper, TypeMirror sourceType, TypeMirror targetType) {
        return mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> typesMatch(m.getParameters().get(0).getType(), sourceType))
                .filter(m -> typesMatch(m.getReturnType(), targetType))
                .findFirst()
                .orElse(null);
    }

    private TypeNode ensureTypeNode(DirectedWeightedMultigraph<GraphNode, GraphEdge> graph, TypeMirror type) {
        TypeNode node = new TypeNode(type, type.toString());
        graph.addVertex(node);
        return node;
    }

    private @Nullable CreationDescriptor findCreationDescriptor(TypeElement type) {
        return creationStrategies.stream()
                .filter(strategy -> strategy.canCreate(type, processingEnv))
                .findFirst()
                .map(strategy -> strategy.describe(type, processingEnv))
                .orElse(null);
    }

    private @Nullable TypeElement asTypeElement(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) typeMirror).asElement();
        }
        return null;
    }

    private Set<Property> discoverProperties(TypeElement type) {
        return propertyStrategies.stream()
                .flatMap(strategy -> strategy.discoverProperties(type, processingEnv).stream())
                .collect(toSet());
    }
}
