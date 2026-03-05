package io.github.joke.percolate.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
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
import io.github.joke.percolate.spi.impl.EnumProvider;
import java.io.IOException;
import java.util.ArrayDeque;
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
    CodeGenStage(final Filer filer, final Types types) {
        this.filer = filer;
        this.types = types;
    }

    public void execute(final OptimizedGraphResult optimizedResult) {
        final var graph = optimizedResult.lazyGraph();

        optimizedResult.graphResult().getMappers().forEach(mapper -> generateMapper(graph, mapper));
    }

    private void generateMapper(final LazyMappingGraph graph, final MapperDefinition mapper) {
        final var mapperName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName());
        final var implName = ClassName.get(mapper.getPackageName(), mapper.getSimpleName() + "Impl");

        final var methods = mapper.getMethods().stream()
                .filter(MethodDefinition::isAbstract)
                .map(method -> generateMethod(graph, method))
                .collect(toUnmodifiableList());

        final var typeSpec = TypeSpec.classBuilder(implName)
                .addModifiers(PUBLIC)
                .addSuperinterface(mapperName)
                .addMethods(methods)
                .build();

        final var javaFile = JavaFile.builder(mapper.getPackageName(), typeSpec).build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to write generated source for " + implName, ioException);
        }
    }

    private MethodSpec generateMethod(final LazyMappingGraph graph, final MethodDefinition method) {
        final var returnType = method.getReturnType();
        final var returnTypeQualified = getQualifiedName(returnType);

        // Find the ConstructorNode for the return type
        final var constructorNode = graph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .filter(cn -> cn.getTargetType().getQualifiedName().toString().equals(returnTypeQualified))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConstructorNode found for " + returnType));

        final var descriptor = constructorNode.getDescriptor();
        final var parameters = descriptor.getParameters();

        // Get ConstructorParamEdges sorted by parameter index
        final var paramEdges = graph.incomingEdgesOf(constructorNode).stream()
                .filter(ConstructorParamEdge.class::isInstance)
                .map(ConstructorParamEdge.class::cast)
                .sorted(comparingInt(ConstructorParamEdge::getParameterIndex))
                .collect(toUnmodifiableList());

        // Build argument expressions in constructor parameter order
        final var args = parameters.stream()
                .map(param -> buildArgExpression(graph, paramEdges, param))
                .collect(toUnmodifiableList());

        final var methodBuilder = MethodSpec.methodBuilder(method.getName())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(returnType));

        method.getParameters().forEach(parameter -> methodBuilder.addParameter(TypeName.get(parameter.getType()), parameter.getName()));

        final var argsJoined = String.join(", ", args);
        methodBuilder.addStatement("return new $L($L)", returnTypeQualified, argsJoined);

        return methodBuilder.build();
    }

    private String buildArgExpression(
            final LazyMappingGraph graph,
            final List<ConstructorParamEdge> paramEdges,
            final Property param) {
        final var edge = paramEdges.stream()
                .filter(paramEdge -> paramEdge.getParameterName().equals(param.getName()))
                .findFirst()
                .orElse(null);

        if (edge == null) {
            return "null /* unmapped: " + param.getName() + " */";
        }

        final var sourceNode = graph.getEdgeSource(edge);
        final var expression = buildExpression(graph, sourceNode);

        // Apply type conversion from graph edges if types differ
        return applyConversionFromGraph(graph, sourceNode, expression, param.getType());
    }

    private String applyConversionFromGraph(
            final LazyMappingGraph graph,
            final GraphNode sourceNode,
            final String baseExpression,
            final TypeMirror targetType) {
        final var sourceType = getNodeType(sourceNode);
        if (sourceType != null && types.isSameType(sourceType, targetType)) {
            return baseExpression;
        }
        // Check enum-to-enum conversion (not modeled as ConversionEdge in the graph)
        if (sourceType != null && EnumProvider.canConvertEnums(sourceType, targetType)) {
            final var enumEdge = EnumProvider.createEnumEdge(sourceType, targetType);
            return enumEdge.getExpressionTemplate().replace("$expr", baseExpression);
        }
        // Find conversion path from source to target via BFS on ConversionEdges
        final var path = findConversionPath(graph, sourceNode, targetType);
        return path.stream().reduce(
                baseExpression,
                (expr, conversionEdge) -> conversionEdge.getExpressionTemplate().replace("$expr", expr),
                (a, b) -> b);
    }

    private List<ConversionEdge> findConversionPath(
            final LazyMappingGraph graph, final GraphNode start, final TypeMirror targetType) {
        // BFS to find shortest path of ConversionEdges reaching targetType
        final var queue = new ArrayDeque<List<ConversionEdge>>();
        // Seed with each outgoing ConversionEdge from start
        graph.outgoingEdgesOf(start).stream()
                .filter(ConversionEdge.class::isInstance)
                .map(ConversionEdge.class::cast)
                .forEach(conversionEdge -> {
                    final var initial = new ArrayList<ConversionEdge>();
                    initial.add(conversionEdge);
                    queue.add(initial);
                });
        final var maxDepth = 5;
        while (!queue.isEmpty()) {
            final var path = queue.poll();
            if (path.size() > maxDepth) {
                break;
            }
            final var last = path.get(path.size() - 1);
            if (types.isSameType(last.getTargetType(), targetType)) {
                return path;
            }
            final var target = graph.getEdgeTarget(last);
            graph.outgoingEdgesOf(target).stream()
                    .filter(ConversionEdge.class::isInstance)
                    .map(ConversionEdge.class::cast)
                    .forEach(conversionEdge -> {
                        final var extended = new ArrayList<>(path);
                        extended.add(conversionEdge);
                        queue.add(extended);
                    });
        }
        return List.of();
    }

    private @Nullable TypeMirror getNodeType(final GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }

    private String buildExpression(final LazyMappingGraph graph, final GraphNode node) {
        return buildExpressionChain(graph, node);
    }

    private String buildExpressionChain(final LazyMappingGraph graph, final GraphNode node) {
        if (!(node instanceof PropertyNode)) {
            if (node instanceof TypeNode) {
                return ((TypeNode) node).getLabel();
            }
            return "";
        }
        final var propertyNode = (PropertyNode) node;
        final var property = propertyNode.getProperty();
        final var accessor = property.getAccessor() instanceof ExecutableElement
                ? ((ExecutableElement) property.getAccessor()).getSimpleName().toString() + "()"
                : property.getName();

        // Walk up to parent via incoming PropertyAccessEdge
        final var parentViaEdge = findParentViaPropertyAccess(graph, node);
        final var parent = parentViaEdge != null ? parentViaEdge : propertyNode.getParent();
        final var parentExpr = buildExpressionChain(graph, parent);
        return parentExpr.isEmpty() ? accessor : parentExpr + "." + accessor;
    }

    private @Nullable GraphNode findParentViaPropertyAccess(final LazyMappingGraph graph, final GraphNode node) {
        return graph.incomingEdgesOf(node).stream()
                .filter(PropertyAccessEdge.class::isInstance)
                .map(graph::getEdgeSource)
                .findFirst()
                .orElse(null);
    }

    private String getQualifiedName(final TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return ((DeclaredType) typeMirror).asElement().toString();
        }
        return typeMirror.toString();
    }
}
