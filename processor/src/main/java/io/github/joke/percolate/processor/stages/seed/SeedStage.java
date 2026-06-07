package io.github.joke.percolate.processor.stages.seed;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupId;
import io.github.joke.percolate.processor.graph.Location;
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
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Populates the {@link MapperGraph} with the structural scaffolding of every directive: parameter roots, the
 * return root, the source and target path chains, and the directive-bridging edge. Each scaffolding edge is
 * recorded as a seed-stage {@link ExpansionGroup} demand by tagging its endpoints with a shared
 * {@link GroupId}; the group is a non-traversable label (it carries no codegen, no slots, no view — the view and
 * the demand inputs are derived from the tags and edges). Three structural demand kinds are registered:
 *
 * <ul>
 *   <li><strong>path-segment</strong> and <strong>directive-binding</strong> — one source-side demand per edge
 *       ({@code root = edge.to}, the single input is {@code edge.from}); each is a fresh group.</li>
 *   <li><strong>assembly (umbrella)</strong> — exactly one demand per parent target node that has child target
 *       leaves ({@code root = parent}, inputs = all child leaves), get-or-created as the chain is built so a
 *       parent that is also a directive-binding root keeps the two demands distinct.</li>
 * </ul>
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SeedStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        ctx.setGraph(apply(mappings));
    }

    MapperGraph apply(final MapperMappings mappings) {
        final var graph = new MapperGraph();
        final var canon = new Canonicalizer(graph);
        for (final var methodMappings : mappings.getMethods()) {
            seedMethod(graph, canon, methodMappings);
        }
        return graph;
    }

    private void seedMethod(final MapperGraph graph, final Canonicalizer canon, final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var scope = new MethodScope(method);

        for (final var param : method.getParameters()) {
            final var loc =
                    new SourceLocation(AccessPath.of(param.getSimpleName().toString()));
            final var node = new Node(Optional.of(param.asType()), loc, scope);
            graph.addNode(node);
            canon.register(node);
        }

        final var returnRoot =
                new Node(Optional.of(method.getReturnType()), new TargetLocation(TargetPath.of("")), scope);
        graph.addNode(returnRoot);
        canon.register(returnRoot);

        final var umbrellas = new HashMap<Node, GroupId>();
        for (final var directive : methodMappings.getDirectives()) {
            seedDirective(graph, canon, scope, directive, returnRoot, umbrellas);
        }
    }

    private void seedDirective(
            final MapperGraph graph,
            final Canonicalizer canon,
            final Scope scope,
            final MappingDirective directive,
            final Node returnRoot,
            final Map<Node, GroupId> umbrellas) {
        final var sourceSegments = splitPath(directive.getSource());
        // ValidateSourceParametersStage is a hard precondition: a validated directive always has a non-empty
        // source whose first segment names a parameter, so there is no empty-source drop and no orphan-source
        // fallback branch here.
        final var deepestSource = buildSourceChain(graph, canon, scope, sourceSegments);
        final var deepestTarget =
                buildTargetChain(graph, canon, scope, splitPath(directive.getTarget()), returnRoot, umbrellas);

        // The directive-bridging edge is emitted once per MappingDirective. The degenerate case of two directives
        // sharing both source and target is guarded producer-side by a single existence check, not by a graph
        // value-dedup index (design D3/D5).
        if (graph.underlyingGraph().getAllEdges(deepestSource, deepestTarget).isEmpty()) {
            graph.addEdge(deepestSource, deepestTarget, Edge.seed(Optional.of(directive.getMirror())));
            registerDemand(graph, deepestTarget, deepestSource);
        }
    }

    /**
     * Builds (or reuses) the source path chain and returns its deepest variable. A single-segment source whose
     * segment names a parameter resolves directly to the typed parameter root (no untyped twin is minted); a
     * multi-segment source extends from that root through untyped path nodes, registering one path-segment demand
     * per extension edge. A shared prefix reuses the canonical node, so its producer edge and demand are emitted
     * only when the node is freshly created (design D3).
     */
    private Node buildSourceChain(
            final MapperGraph graph, final Canonicalizer canon, final Scope scope, final List<String> segments) {
        var previous = canon.canonical(scope, new SourceLocation(new AccessPath(List.of(segments.get(0)))))
                .getNode();
        for (var i = 1; i < segments.size(); i++) {
            final var loc = new SourceLocation(new AccessPath(List.copyOf(segments.subList(0, i + 1))));
            final var resolved = canon.canonical(scope, loc);
            final var node = resolved.getNode();
            if (resolved.isCreated()) {
                // Structural chain edges carry no @Map mirror: a shared path prefix produces one edge (and so one
                // path-segment demand) across directives. The directive is read from the bridging edge.
                graph.addEdge(previous, node, Edge.seed(Optional.empty()));
                registerDemand(graph, node, previous);
            }
            previous = node;
        }
        return previous;
    }

    private Node buildTargetChain(
            final MapperGraph graph,
            final Canonicalizer canon,
            final Scope scope,
            final List<String> segments,
            final Node returnRoot,
            final Map<Node, GroupId> umbrellas) {
        var previous = returnRoot;
        for (var i = 1; i <= segments.size(); i++) {
            final var loc = new TargetLocation(new TargetPath(List.copyOf(segments.subList(0, i))));
            final var resolved = canon.canonical(scope, loc);
            final var node = resolved.getNode();
            final var parent = previous;
            if (resolved.isCreated()) {
                graph.addEdge(node, parent, Edge.seed(Optional.empty()));
                tagUmbrellaChild(graph, parent, node, umbrellas);
            }
            previous = node;
        }
        return previous;
    }

    /** Registers a fresh source-side seed demand: {@code root}'s producer is {@code input}. */
    private void registerDemand(final MapperGraph graph, final Node root, final Node input) {
        final var id = GroupId.next(true);
        graph.addGroup(new ExpansionGroup(id, root, graph));
        root.joinGroup(id);
        input.joinGroup(id);
    }

    /** Tags {@code child} into {@code parent}'s single umbrella assembly demand, creating it on first child. */
    private void tagUmbrellaChild(
            final MapperGraph graph, final Node parent, final Node child, final Map<Node, GroupId> umbrellas) {
        final var id = umbrellas.computeIfAbsent(parent, root -> {
            final var fresh = GroupId.next(true);
            graph.addGroup(new ExpansionGroup(fresh, root, graph));
            root.joinGroup(fresh);
            return fresh;
        });
        child.joinGroup(id);
    }

    private static List<String> splitPath(final String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }

    /**
     * Owns the {@code (scope, location) -> Node} canonical map for one {@link #apply} invocation, so a shared path
     * prefix reuses one {@link Node} instance without the {@link MapperGraph} holding any seed-time knowledge
     * (design D2). A structural variable is created untyped on first request and added to the graph; later requests
     * for the same key reuse it. Already-typed nodes (parameter roots, the return root) are pre-{@link #register}ed
     * so the chains share them. Expansion-minted nodes never route through here — they rely on instance identity.
     */
    @RequiredArgsConstructor
    private static final class Canonicalizer {

        private final MapperGraph graph;

        @SuppressWarnings("PMD.UseConcurrentHashMap") // local, single-threaded per-apply canonical map
        private final Map<VariableKey, Node> index = new HashMap<>();

        /** The canonical node for {@code (scope, loc)}, creating it untyped on first request (design D2/D3). */
        Canonical canonical(final Scope scope, final Location loc) {
            final var key = new VariableKey(scope, loc);
            final var existing = index.get(key);
            if (existing != null) {
                return new Canonical(existing, false);
            }
            final var node = new Node(Optional.empty(), loc, scope);
            graph.addNode(node);
            index.put(key, node);
            return new Canonical(node, true);
        }

        /** Registers an already-created (typed) node as the canonical variable for its {@code (scope, location)}. */
        void register(final Node node) {
            index.putIfAbsent(new VariableKey(node.getScope(), node.getLoc()), node);
        }
    }

    /** The canonical node for a {@code (scope, location)} request plus whether this request created it. */
    @Value
    private static class Canonical {
        Node node;
        boolean created;
    }

    /** Value key for the canonical map: the {@code (scope, location)} a structural variable is canonical for. */
    @Value
    private static class VariableKey {
        Scope scope;
        Location location;
    }
}
