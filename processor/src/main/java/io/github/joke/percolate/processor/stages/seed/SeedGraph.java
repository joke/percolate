package io.github.joke.percolate.processor.stages.seed;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.spi.GroupCodegen;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SeedGraph implements Stage {

    static final String STRATEGY_FQN = "io.github.joke.percolate.processor.stages.seed.SeedGraph";
    private static final GroupCodegen PLACEHOLDER_CODEGEN =
            (vars, inputs) -> CodeBlock.of("/* unresolved seed-group */");

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        final var graph = apply(mappings);
        ctx.setGraph(graph);
    }

    MapperGraph apply(final MapperMappings mappings) {
        final var graph = new MapperGraph();
        for (final var methodMappings : mappings.getMethods()) {
            seedMethod(graph, methodMappings);
        }
        return graph;
    }

    private void seedMethod(final MapperGraph graph, final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var scope = new MethodScope(method);
        final var returnType = method.getReturnType();

        final var paramRoots = new LinkedHashMap<String, Node>();
        for (final var param : method.getParameters()) {
            final var paramName = param.getSimpleName().toString();
            final var loc = new SourceLocation(AccessPath.of(paramName));
            final var node = new Node(Optional.of(param.asType()), loc, scope);
            graph.addNode(node);
            paramRoots.put(paramName, node);
        }

        final var returnRootLoc = new TargetLocation(TargetPath.of(""));
        final var returnRoot = new Node(Optional.of(returnType), returnRootLoc, scope);
        graph.addNode(returnRoot);

        final var sourceCache = new HashMap<List<String>, Node>();
        final var targetCache = new HashMap<List<String>, Node>();

        for (final var directive : methodMappings.getDirectives()) {
            seedDirective(graph, scope, directive, paramRoots, returnRoot, sourceCache, targetCache);
        }
    }

    private void seedDirective(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final Map<String, Node> paramRoots,
            final Node returnRoot,
            final Map<List<String>, Node> sourceCache,
            final Map<List<String>, Node> targetCache) {
        final var sourceSegments = splitPath(directive.getSource());
        final var targetSegments = splitPath(directive.getTarget());

        final var deepestSource = buildSourceChain(graph, scope, directive, sourceSegments, paramRoots, sourceCache);
        if (deepestSource == null) {
            return;
        }
        final var deepestTarget = buildTargetChain(graph, scope, directive, targetSegments, returnRoot, targetCache);

        final var bridgingEdge =
                Edge.seed(deepestSource, deepestTarget, Optional.of(directive.getMirror()), Optional.empty());
        if (graph.addEdge(bridgingEdge)) {
            registerSeedGroup(graph, bridgingEdge);
        }
    }

    @Nullable
    private Node buildSourceChain(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final List<String> sourceSegments,
            final Map<String, Node> paramRoots,
            final Map<List<String>, Node> sourceCache) {
        if (sourceSegments.isEmpty()) {
            return null;
        }
        final var firstSegment = sourceSegments.get(0);
        Node previous = paramRoots.get(firstSegment);
        if (previous == null) {
            final var key = List.copyOf(sourceSegments.subList(0, 1));
            previous = sourceCache.computeIfAbsent(key, k -> {
                final var node = new Node(Optional.empty(), new SourceLocation(new AccessPath(k)), scope);
                graph.addNode(node);
                return node;
            });
        }
        for (var i = 1; i < sourceSegments.size(); i++) {
            final var key = List.copyOf(sourceSegments.subList(0, i + 1));
            final var prev = previous;
            final var node = sourceCache.computeIfAbsent(key, k -> {
                final var fresh = new Node(Optional.empty(), new SourceLocation(new AccessPath(k)), scope);
                graph.addNode(fresh);
                final var edge = Edge.seed(prev, fresh, Optional.of(directive.getMirror()), Optional.empty());
                graph.addEdge(edge);
                registerSeedGroup(graph, edge);
                return fresh;
            });
            previous = node;
        }
        return previous;
    }

    private Node buildTargetChain(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final List<String> targetSegments,
            final Node returnRoot,
            final Map<List<String>, Node> targetCache) {
        Node previous = returnRoot;
        for (var i = 1; i <= targetSegments.size(); i++) {
            final var key = List.copyOf(targetSegments.subList(0, i));
            final var prev = previous;
            previous = targetCache.computeIfAbsent(key, k -> {
                final var fresh = new Node(Optional.empty(), new TargetLocation(new TargetPath(k)), scope);
                graph.addNode(fresh);
                final var edge = Edge.seed(fresh, prev, Optional.of(directive.getMirror()), Optional.empty());
                graph.addEdge(edge);
                registerSeedGroup(graph, edge);
                return fresh;
            });
        }
        return previous;
    }

    private void registerSeedGroup(final MapperGraph graph, final Edge seedEdge) {
        final var group = ExpansionGroup.of(
                seedEdge.getTo(), List.of(seedEdge.getFrom()), PLACEHOLDER_CODEGEN, STRATEGY_FQN, Set.of(), graph);
        graph.addGroup(group);
    }

    private static List<String> splitPath(final String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
