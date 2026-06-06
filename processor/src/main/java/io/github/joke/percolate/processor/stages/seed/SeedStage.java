package io.github.joke.percolate.processor.stages.seed;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.GroupId;
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

    static final String STRATEGY_FQN = "io.github.joke.percolate.processor.stages.seed.SeedStage";

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
        for (final var methodMappings : mappings.getMethods()) {
            seedMethod(graph, methodMappings);
        }
        return graph;
    }

    private void seedMethod(final MapperGraph graph, final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var scope = new MethodScope(method);

        for (final var param : method.getParameters()) {
            final var loc = new SourceLocation(AccessPath.of(param.getSimpleName().toString()));
            final var node = new Node(Optional.of(param.asType()), loc, scope);
            graph.addNode(node);
            graph.registerVariable(node);
        }

        final var returnRoot = new Node(Optional.of(method.getReturnType()), new TargetLocation(TargetPath.of("")), scope);
        graph.addNode(returnRoot);
        graph.registerVariable(returnRoot);

        final var umbrellas = new HashMap<Node, GroupId>();
        for (final var directive : methodMappings.getDirectives()) {
            seedDirective(graph, scope, directive, returnRoot, umbrellas);
        }
    }

    private void seedDirective(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final Node returnRoot,
            final Map<Node, GroupId> umbrellas) {
        final var sourceSegments = splitPath(directive.getSource());
        // ValidateSourceParametersStage is a hard precondition: a validated directive always has a non-empty
        // source whose first segment names a parameter, so there is no empty-source drop and no orphan-source
        // fallback branch here.
        final var deepestSource = buildSourceChain(graph, scope, directive, sourceSegments);
        final var deepestTarget =
                buildTargetChain(graph, scope, directive, splitPath(directive.getTarget()), returnRoot, umbrellas);

        final var bridgingEdge =
                Edge.seed(deepestSource, deepestTarget, Optional.of(directive.getMirror()), Optional.empty());
        if (graph.addEdge(bridgingEdge)) {
            registerDemand(graph, bridgingEdge.getTo(), bridgingEdge.getFrom());
        }
    }

    /**
     * Builds (or reuses) the source path chain and returns its deepest variable. A single-segment source whose
     * segment names a parameter resolves directly to the typed parameter root (no untyped twin is minted); a
     * multi-segment source extends from that root through untyped path nodes, registering one path-segment demand
     * per extension edge.
     */
    private Node buildSourceChain(
            final MapperGraph graph, final Scope scope, final MappingDirective directive, final List<String> segments) {
        var previous = graph.variableFor(scope, new SourceLocation(new AccessPath(List.of(segments.get(0)))));
        for (var i = 1; i < segments.size(); i++) {
            final var loc = new SourceLocation(new AccessPath(List.copyOf(segments.subList(0, i + 1))));
            final var node = graph.variableFor(scope, loc);
            final var edge = Edge.seed(previous, node, Optional.of(directive.getMirror()), Optional.empty());
            if (graph.addEdge(edge)) {
                registerDemand(graph, node, previous);
            }
            previous = node;
        }
        return previous;
    }

    private Node buildTargetChain(
            final MapperGraph graph,
            final Scope scope,
            final MappingDirective directive,
            final List<String> segments,
            final Node returnRoot,
            final Map<Node, GroupId> umbrellas) {
        var previous = returnRoot;
        for (var i = 1; i <= segments.size(); i++) {
            final var loc = new TargetLocation(new TargetPath(List.copyOf(segments.subList(0, i))));
            final var node = graph.variableFor(scope, loc);
            final var parent = previous;
            final var edge = Edge.seed(node, parent, Optional.of(directive.getMirror()), Optional.empty());
            if (graph.addEdge(edge)) {
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
}
