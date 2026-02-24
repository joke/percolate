package io.github.joke.percolate.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.LazyMappingGraph;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.graph.edge.PropertyAccessEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.impl.EnumProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class CodeGenStage {

    private final Filer filer;
    private final Types types;

    @Inject
    CodeGenStage(Filer filer, Types types) {
        this.filer = filer;
        this.types = types;
    }

    public void execute(OptimizedGraphResult optimizedResult) {
        LazyMappingGraph graph = optimizedResult.lazyGraph();

        optimizedResult.graphResult().getMappers().forEach(mapper -> generateMapper(graph, mapper));
    }

    private void generateMapper(LazyMappingGraph graph, MapperDefinition mapper) {
        ClassName mapperName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName());
        ClassName implName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName() + "Impl");

        List<MethodSpec> methods = mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .map(method -> generateMethod(graph, method))
                .collect(toList());

        TypeSpec typeSpec = TypeSpec.classBuilder(implName)
                .addModifiers(PUBLIC)
                .addSuperinterface(mapperName)
                .addMethods(methods)
                .build();

        JavaFile javaFile = JavaFile.builder(mapper.getPackageName(), typeSpec).build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write generated source for " + implName, e);
        }
    }

    private MethodSpec generateMethod(LazyMappingGraph graph, MethodDefinition method) {
        TypeMirror returnType = method.getReturnType();
        String returnTypeQualified = getQualifiedName(returnType);

        // Find the ConstructorNode for the return type
        ConstructorNode constructorNode = graph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .filter(cn -> cn.getTargetType().getQualifiedName().toString().equals(returnTypeQualified))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConstructorNode found for " + returnType));

        CreationDescriptor descriptor = constructorNode.getDescriptor();
        List<Property> parameters = descriptor.getParameters();

        // Get ConstructorParamEdges sorted by parameter index
        List<ConstructorParamEdge> paramEdges = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .sorted(comparingInt(ConstructorParamEdge::getParameterIndex))
                .collect(toList());

        // Build argument expressions in constructor parameter order
        List<String> args = new ArrayList<>();
        for (Property param : parameters) {
            @Nullable
            ConstructorParamEdge edge = paramEdges.stream()
                    .filter(e -> e.getParameterName().equals(param.getName()))
                    .findFirst()
                    .orElse(null);

            if (edge == null) {
                args.add("null /* unmapped: " + param.getName() + " */");
                continue;
            }

            GraphNode sourceNode = graph.getEdgeSource(edge);
            String expression = buildExpression(graph, sourceNode);

            // Apply type conversion from graph edges if types differ
            TypeMirror targetType = param.getType();
            expression = applyConversionFromGraph(graph, sourceNode, expression, targetType);

            args.add(expression);
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getName())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(returnType));

        method.getParameters().forEach(p -> methodBuilder.addParameter(TypeName.get(p.getType()), p.getName()));

        String argsJoined = String.join(", ", args);
        methodBuilder.addStatement("return new $L($L)", returnTypeQualified, argsJoined);

        return methodBuilder.build();
    }

    private String applyConversionFromGraph(
            LazyMappingGraph graph, GraphNode sourceNode, String baseExpression, TypeMirror targetType) {
        @Nullable TypeMirror sourceType = getNodeType(sourceNode);
        if (sourceType != null && types.isSameType(sourceType, targetType)) {
            return baseExpression;
        }
        // Check enum-to-enum conversion (not modeled as ConversionEdge in the graph)
        if (sourceType != null && EnumProvider.canConvertEnums(sourceType, targetType)) {
            ConversionEdge enumEdge = EnumProvider.createEnumEdge(sourceType, targetType);
            return enumEdge.getExpressionTemplate().replace("$expr", baseExpression);
        }
        // Find conversion path from source to target via BFS on ConversionEdges
        List<ConversionEdge> path = findConversionPath(graph, sourceNode, targetType);
        String expression = baseExpression;
        for (ConversionEdge edge : path) {
            expression = edge.getExpressionTemplate().replace("$expr", expression);
        }
        return expression;
    }

    private List<ConversionEdge> findConversionPath(LazyMappingGraph graph, GraphNode start, TypeMirror targetType) {
        // BFS to find shortest path of ConversionEdges reaching targetType
        java.util.Queue<List<ConversionEdge>> queue = new java.util.ArrayDeque<>();
        // Seed with each outgoing ConversionEdge from start
        graph.outgoingEdgesOf(start).stream()
                .filter(ConversionEdge.class::isInstance)
                .map(ConversionEdge.class::cast)
                .forEach(e -> {
                    List<ConversionEdge> initial = new ArrayList<>();
                    initial.add(e);
                    queue.add(initial);
                });
        int maxDepth = 5;
        while (!queue.isEmpty()) {
            List<ConversionEdge> path = queue.poll();
            if (path.size() > maxDepth) {
                break;
            }
            ConversionEdge last = path.get(path.size() - 1);
            if (types.isSameType(last.getTargetType(), targetType)) {
                return path;
            }
            GraphNode target = graph.getEdgeTarget(last);
            graph.outgoingEdgesOf(target).stream()
                    .filter(ConversionEdge.class::isInstance)
                    .map(ConversionEdge.class::cast)
                    .forEach(e -> {
                        List<ConversionEdge> extended = new ArrayList<>(path);
                        extended.add(e);
                        queue.add(extended);
                    });
        }
        return new ArrayList<>();
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

    private String buildExpression(LazyMappingGraph graph, GraphNode node) {
        List<String> chain = new ArrayList<>();
        GraphNode current = node;

        while (current instanceof PropertyNode) {
            PropertyNode propertyNode = (PropertyNode) current;
            Property property = propertyNode.getProperty();

            if (property.getAccessor() instanceof ExecutableElement) {
                chain.add(
                        0,
                        ((ExecutableElement) property.getAccessor())
                                        .getSimpleName()
                                        .toString() + "()");
            } else {
                chain.add(0, property.getName());
            }

            // Walk up to parent via incoming PropertyAccessEdge
            @Nullable GraphNode parent = findParentViaPropertyAccess(graph, current);
            if (parent == null) {
                // fallback: use the stored parent reference
                parent = propertyNode.getParent();
            }
            current = parent;
        }

        if (current instanceof TypeNode) {
            chain.add(0, ((TypeNode) current).getLabel());
        }

        return String.join(".", chain);
    }

    private @Nullable GraphNode findParentViaPropertyAccess(LazyMappingGraph graph, GraphNode node) {
        return graph.incomingEdgesOf(node).stream()
                .filter(PropertyAccessEdge.class::isInstance)
                .map(graph::getEdgeSource)
                .findFirst()
                .orElse(null);
    }

    private String getQualifiedName(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return ((DeclaredType) typeMirror).asElement().toString();
        }
        return typeMirror.toString();
    }
}
