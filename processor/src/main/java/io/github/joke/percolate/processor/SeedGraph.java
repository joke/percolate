package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.graph.Edge;
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
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SeedGraph {

    private static final int EDGE_WEIGHT = 1;

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

        // Emit parameter-root nodes
        final var paramRoots = new LinkedHashMap<String, Node>();
        for (final var param : method.getParameters()) {
            final var paramName = param.getSimpleName().toString();
            final var paramType = param.asType();
            final var loc = new SourceLocation(io.github.joke.percolate.processor.graph.AccessPath.of(paramName));
            final var node = new Node(Optional.of(paramType), loc, scope);
            graph.addNode(node);
            paramRoots.put(paramName, node);
        }

        // Emit return-type root node
        final var returnRootLoc = new TargetLocation(TargetPath.of(""));
        final var returnRoot = new Node(Optional.of(returnType), returnRootLoc, scope);
        graph.addNode(returnRoot);

        // Process directives
        for (final var directive : methodMappings.getDirectives()) {
            seedDirective(graph, scope, directive, paramRoots, returnRoot);
        }
    }

    private void seedDirective(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final Map<String, Node> paramRoots,
            final Node returnRoot) {
        final var sourceSegments = splitPath(directive.getSource());
        final var targetSegments = splitPath(directive.getTarget());

        // Source chain: parameter-root → [s1] → [s1,s2] → ... → [s1,...,sk]
        final var sourceNodes = new ArrayDeque<Node>();
        final var firstSourceSegment = sourceSegments.get(0);
        Node sourceChainStart;
        if (sourceSegments.size() == 1) {
            // Single-segment: spec requires a separate empty-typed source node
            final var loc =
                    new SourceLocation(io.github.joke.percolate.processor.graph.AccessPath.of(firstSourceSegment));
            sourceChainStart = new Node(Optional.empty(), loc, scope);
            graph.addNode(sourceChainStart);
            final var paramRoot = paramRoots.get(firstSourceSegment);
            if (paramRoot != null) {
                graph.addEdge(new Edge(paramRoot, sourceChainStart, EDGE_WEIGHT, Optional.of(directive.getMirror())));
            }
        } else {
            // Multi-segment: MAY reuse parameter-root as chain start
            sourceChainStart = paramRoots.get(firstSourceSegment);
            if (sourceChainStart == null) {
                final var loc =
                        new SourceLocation(io.github.joke.percolate.processor.graph.AccessPath.of(firstSourceSegment));
                sourceChainStart = new Node(Optional.empty(), loc, scope);
                graph.addNode(sourceChainStart);
            }
        }
        sourceNodes.add(sourceChainStart);

        // Build chain for remaining source segments
        for (int i = 1; i < sourceSegments.size(); i++) {
            final var seg = sourceSegments.get(i);
            final var prevNode = sourceNodes.peekLast();
            final var prevPath = ((SourceLocation) prevNode.getLoc()).getPath();
            final var newPath = prevPath.append(seg);
            final var newNode = new Node(Optional.empty(), new SourceLocation(newPath), scope);
            graph.addNode(newNode);
            graph.addEdge(new Edge(prevNode, newNode, EDGE_WEIGHT, Optional.of(directive.getMirror())));
            sourceNodes.add(newNode);
        }

        // Target chain: [t1,...,tk] → [t1,...,t(k-1)] → ... → [] (return root)
        // Build outward from root: [] → [t1] → [t1,t2] → ... → [t1,...,tk]
        // Then reverse: edges flow from deepest to root
        Node currentTarget = returnRoot;
        for (final var seg : targetSegments) {
            final var prevPath = ((TargetLocation) currentTarget.getLoc()).getPath();
            final var newPath = prevPath.append(seg);
            final var newNode = new Node(Optional.empty(), new TargetLocation(newPath), scope);
            graph.addNode(newNode);
            graph.addEdge(new Edge(newNode, currentTarget, EDGE_WEIGHT, Optional.of(directive.getMirror())));
            currentTarget = newNode;
        }

        // Bridging edge: deepest source → deepest target
        final var deepestSource = sourceNodes.peekLast();
        final var deepestTarget = currentTarget;
        if (deepestSource != null && deepestTarget != null) {
            graph.addEdge(new Edge(deepestSource, deepestTarget, EDGE_WEIGHT, Optional.of(directive.getMirror())));
        }
    }

    private static List<String> splitPath(final String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
