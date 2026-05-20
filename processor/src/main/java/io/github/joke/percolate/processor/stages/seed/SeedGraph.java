package io.github.joke.percolate.processor.stages.seed;

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
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.GroupCodegen;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ResolvedSegment;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SeedGraph implements Stage {

    private static final int SINGLE_SEGMENT_COUNT = 1;

    private final List<PathSegmentResolver> pathSegmentResolvers;
    private final ResolveCtx resolveCtx;

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

        // Emit parameter-root nodes
        final var paramRoots = new LinkedHashMap<String, Node>();
        for (final var param : method.getParameters()) {
            final var paramName = param.getSimpleName().toString();
            final var paramType = param.asType();
            final var loc = new SourceLocation(AccessPath.of(paramName));
            final var node = new Node(Optional.of(paramType), loc, scope);
            graph.addNode(node);
            paramRoots.put(paramName, node);
        }

        // Emit return-type root node
        final var returnRootLoc = new TargetLocation(TargetPath.of(""));
        final var returnRoot = new Node(Optional.of(returnType), returnRootLoc, scope);
        graph.addNode(returnRoot);

        // Per-method-scope cache for typed source nodes keyed by full path segments
        final var typedSourceCache = new HashMap<List<String>, Node>();

        // Process directives
        for (final var directive : methodMappings.getDirectives()) {
            seedDirective(graph, scope, directive, paramRoots, typedSourceCache, returnRoot);
        }
    }

    private void seedDirective(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final Map<String, Node> paramRoots,
            final Map<List<String>, Node> typedSourceCache,
            final Node returnRoot) {
        final var sourceSegments = splitPath(directive.getSource());
        final var targetSegments = splitPath(directive.getTarget());

        // Untyped source chain: parameter-root → [s1] → [s1,s2] → ... → [s1,...,sk]
        final var untypedSourceChain = buildUntypedSourceChain(graph, scope, directive, sourceSegments, paramRoots);

        // Typed source chain via PathSegmentResolvers (only for multi-segment paths)
        final var deepestTypedSource =
                resolveTypedSourceChain(graph, scope, sourceSegments, untypedSourceChain, typedSourceCache);

        // Target chain
        var currentTarget = returnRoot;
        for (final var seg : targetSegments) {
            final var prevPath = ((TargetLocation) currentTarget.getLoc()).getPath();
            final var newPath = prevPath.append(seg);
            final var newNode = new Node(Optional.empty(), new TargetLocation(newPath), scope);
            graph.addNode(newNode);
            graph.addEdge(Edge.seed(newNode, currentTarget, Optional.of(directive.getMirror()), Optional.empty()));
            currentTarget = newNode;
        }

        // Bridging edge: deepest source → deepest target.
        // When the full source path resolved typed, originate from the typed node; otherwise from the untyped leaf.
        final var bridgeFrom = deepestTypedSource.orElseGet(untypedSourceChain::peekLast);
        if (bridgeFrom != null) {
            graph.addEdge(Edge.seed(bridgeFrom, currentTarget, Optional.of(directive.getMirror()), Optional.empty()));
        }
    }

    private Deque<Node> buildUntypedSourceChain(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final List<String> sourceSegments,
            final Map<String, Node> paramRoots) {
        final var sourceNodes = new ArrayDeque<Node>();
        final var firstSegment = sourceSegments.get(0);
        Node sourceChainStart;
        if (sourceSegments.size() == SINGLE_SEGMENT_COUNT) {
            final var loc = new SourceLocation(AccessPath.of(firstSegment));
            sourceChainStart = new Node(Optional.empty(), loc, scope);
            graph.addNode(sourceChainStart);
            final var paramRoot = paramRoots.get(firstSegment);
            if (paramRoot != null) {
                graph.addEdge(
                        Edge.seed(paramRoot, sourceChainStart, Optional.of(directive.getMirror()), Optional.empty()));
            }
        } else {
            sourceChainStart = paramRoots.get(firstSegment);
            if (sourceChainStart == null) {
                final var loc = new SourceLocation(AccessPath.of(firstSegment));
                sourceChainStart = new Node(Optional.empty(), loc, scope);
                graph.addNode(sourceChainStart);
            }
        }
        sourceNodes.add(sourceChainStart);

        for (var i = 1; i < sourceSegments.size(); i++) {
            final var seg = sourceSegments.get(i);
            final var prevNode = sourceNodes.peekLast();
            final var prevPath = ((SourceLocation) prevNode.getLoc()).getPath();
            final var newPath = prevPath.append(seg);
            final var newNode = new Node(Optional.empty(), new SourceLocation(newPath), scope);
            graph.addNode(newNode);
            graph.addEdge(Edge.seed(prevNode, newNode, Optional.of(directive.getMirror()), Optional.empty()));
            sourceNodes.add(newNode);
        }
        return sourceNodes;
    }

    private Optional<Node> resolveTypedSourceChain(
            final MapperGraph graph,
            final Scope scope,
            final List<String> sourceSegments,
            final Deque<Node> untypedSourceChain,
            final Map<List<String>, Node> typedSourceCache) {
        if (sourceSegments.size() <= SINGLE_SEGMENT_COUNT) {
            return Optional.empty();
        }
        final var untypedNodes = new ArrayList<>(untypedSourceChain);
        final var paramRoot = untypedNodes.get(0);
        if (paramRoot.getType().isEmpty()) {
            return Optional.empty();
        }
        var parent = paramRoot;
        for (var i = 1; i < sourceSegments.size(); i++) {
            final var pathKey = List.copyOf(sourceSegments.subList(0, i + 1));
            final var cached = typedSourceCache.get(pathKey);
            if (cached != null) {
                parent = cached;
                continue;
            }
            if (parent.getType().isEmpty()) {
                return Optional.empty();
            }
            final var resolution = findResolution(parent.getType().get(), sourceSegments.get(i));
            if (resolution.isEmpty()) {
                return Optional.empty();
            }
            final var typedNode =
                    registerTypedSegment(graph, scope, pathKey, parent, untypedNodes.get(i), resolution.get());
            typedSourceCache.put(pathKey, typedNode);
            parent = typedNode;
        }
        return Optional.of(parent);
    }

    private Node registerTypedSegment(
            final MapperGraph graph,
            final Scope scope,
            final List<String> pathKey,
            final Node parent,
            final Node untypedLeaf,
            final ResolverMatch match) {
        final var typedNode = new Node(
                Optional.of(match.segment.getReturnType()), new SourceLocation(new AccessPath(pathKey)), scope);
        graph.addNode(typedNode);
        final var strategyFqn = match.resolverClassName;
        final var realisedEdge =
                Edge.realised(parent, typedNode, match.segment.getWeight(), match.segment.getCodegen(), strategyFqn);
        graph.addEdge(realisedEdge);
        graph.addEdge(Edge.marker(untypedLeaf, typedNode, strategyFqn));
        final var group = ExpansionGroup.of(
                typedNode,
                List.of(parent),
                wrapAsGroupCodegen(match.segment.getCodegen()),
                strategyFqn,
                Set.of(realisedEdge),
                graph);
        graph.addGroup(group);
        return typedNode;
    }

    private Optional<ResolverMatch> findResolution(
            final javax.lang.model.type.TypeMirror parentType, final String segment) {
        for (final var resolver : pathSegmentResolvers) {
            final var resolved = resolver.resolve(parentType, segment, resolveCtx);
            if (resolved.isPresent()) {
                return Optional.of(
                        new ResolverMatch(resolved.get(), resolver.getClass().getName()));
            }
        }
        return Optional.empty();
    }

    private static GroupCodegen wrapAsGroupCodegen(final EdgeCodegen edgeCodegen) {
        return edgeCodegen::render;
    }

    private static List<String> splitPath(final String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }

    private static final class ResolverMatch {
        private final ResolvedSegment segment;
        private final String resolverClassName;

        ResolverMatch(final ResolvedSegment segment, final String resolverClassName) {
            this.segment = segment;
            this.resolverClassName = resolverClassName;
        }
    }
}
