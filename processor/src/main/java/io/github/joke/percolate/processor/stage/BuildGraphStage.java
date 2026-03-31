package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.AccessEdge;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.SourceRootNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.model.MapDirective;
import io.github.joke.percolate.processor.model.MapperModel;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jgrapht.graph.DefaultDirectedGraph;

public final class BuildGraphStage {

    @Inject
    BuildGraphStage() {}

    public StageResult<MappingGraph> execute(final MapperModel mapperModel) {
        final Map<MappingMethodModel, DefaultDirectedGraph<Object, Object>> methodGraphs = new LinkedHashMap<>();

        for (final MappingMethodModel method : mapperModel.getMethods()) {
            methodGraphs.put(method, buildMethodGraph(method));
        }

        return StageResult.success(
                new MappingGraph(mapperModel.getMapperType(), List.copyOf(mapperModel.getMethods()), methodGraphs));
    }

    private DefaultDirectedGraph<Object, Object> buildMethodGraph(final MappingMethodModel method) {
        final var graph = new DefaultDirectedGraph<>(Object.class);

        final var paramName =
                method.getMethod().getParameters().get(0).getSimpleName().toString();
        final var sourceRoot = new SourceRootNode(paramName);
        graph.addVertex(sourceRoot);

        final var targetNodes = buildTargetNodes(graph, method);
        final var chainNodes = processDirectives(graph, sourceRoot, method, targetNodes);
        autoMap(graph, sourceRoot, chainNodes, targetNodes, method.getSourceType());

        return graph;
    }

    private Map<String, TargetPropertyNode> buildTargetNodes(
            final DefaultDirectedGraph<Object, Object> graph, final MappingMethodModel method) {
        final Map<String, TargetPropertyNode> targetNodes = new LinkedHashMap<>();

        for (final String name : scanTargetPropertyNames(method.getTargetType())) {
            final var node = new TargetPropertyNode(name);
            targetNodes.put(name, node);
            graph.addVertex(node);
        }

        return targetNodes;
    }

    private Map<String, SourcePropertyNode> processDirectives(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final MappingMethodModel method,
            final Map<String, TargetPropertyNode> targetNodes) {
        final Map<String, SourcePropertyNode> chainNodes = new LinkedHashMap<>();

        for (final MapDirective directive : method.getDirectives()) {
            final var targetNode = getOrCreateTargetNode(graph, targetNodes, directive.getTarget());
            addSourceChain(graph, sourceRoot, chainNodes, directive.getSource(), targetNode);
        }

        return chainNodes;
    }

    private static TargetPropertyNode getOrCreateTargetNode(
            final DefaultDirectedGraph<Object, Object> graph,
            final Map<String, TargetPropertyNode> targetNodes,
            final String name) {
        return targetNodes.computeIfAbsent(name, n -> {
            final var node = new TargetPropertyNode(n);
            graph.addVertex(node);
            return node;
        });
    }

    private static void addSourceChain(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final Map<String, SourcePropertyNode> chainNodes,
            final String sourceExpression,
            final TargetPropertyNode targetNode) {
        final var segments = sourceExpression.split("\\.", -1);

        Object parent = sourceRoot;
        var currentPath = "";

        for (final String segment : segments) {
            final var path = currentPath.isEmpty() ? segment : currentPath + "." + segment;
            final var parentRef = parent;

            final SourcePropertyNode node = chainNodes.computeIfAbsent(path, p -> {
                final var n = new SourcePropertyNode(segment);
                graph.addVertex(n);
                graph.addEdge(parentRef, n, new AccessEdge());
                return n;
            });

            parent = node;
            currentPath = path;
        }

        graph.addEdge(parent, targetNode, new MappingEdge());
    }

    private void autoMap(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final Map<String, SourcePropertyNode> chainNodes,
            final Map<String, TargetPropertyNode> targetNodes,
            final TypeMirror sourceType) {
        final var sourceNames = scanPropertyNames(sourceType);

        targetNodes.entrySet().stream()
                .filter(e -> graph.inDegreeOf(e.getValue()) == 0)
                .filter(e -> sourceNames.contains(e.getKey()))
                .forEach(e -> addSourceChain(graph, sourceRoot, chainNodes, e.getKey(), e.getValue()));
    }

    private Set<String> scanTargetPropertyNames(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return Set.of();
        }
        final var typeElement = (TypeElement) ((DeclaredType) type).asElement();
        final var fromGettersAndFields = typeElement.getEnclosedElements().stream()
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .flatMap(e -> extractPropertyName(e).stream())
                .collect(toUnmodifiableSet());
        final var fromConstructor = scanConstructorParamNames(typeElement);
        final var result = new java.util.LinkedHashSet<>(fromGettersAndFields);
        result.addAll(fromConstructor);
        return Set.copyOf(result);
    }

    private static Set<String> scanConstructorParamNames(final TypeElement typeElement) {
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(e -> (ExecutableElement) e)
                .max(comparingInt(c -> c.getParameters().size()))
                .map(c -> c.getParameters().stream()
                        .map(p -> p.getSimpleName().toString())
                        .collect(toUnmodifiableSet()))
                .orElse(Set.of());
    }

    private Set<String> scanPropertyNames(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return Set.of();
        }
        final var typeElement = (TypeElement) ((DeclaredType) type).asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .flatMap(e -> extractPropertyName(e).stream())
                .collect(toUnmodifiableSet());
    }

    private static java.util.Optional<String> extractPropertyName(final javax.lang.model.element.Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            final var method = (ExecutableElement) element;
            if (!method.getModifiers().contains(Modifier.PUBLIC)
                    || !method.getParameters().isEmpty()
                    || method.getReturnType().getKind() == TypeKind.VOID) {
                return java.util.Optional.empty();
            }
            return getterName(method.getSimpleName().toString());
        }
        if (element.getKind() == ElementKind.FIELD) {
            final var field = (VariableElement) element;
            if (field.getModifiers().contains(Modifier.PUBLIC)) {
                return java.util.Optional.of(element.getSimpleName().toString());
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String> getterName(final String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return java.util.Optional.of(Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return java.util.Optional.of(Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3));
        }
        return java.util.Optional.empty();
    }
}
