package io.github.joke.percolate.processor.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.RealisedSubgraph;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;

@NoArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

    private static final String SEED_PACKAGE_PREFIX = "io.github.joke.percolate.processor.stages.seed.";
    private static final int SINGLE_EDGE = 1;

    List<MethodImpl> build(final MapperContext ctx) {
        final var shape = ctx.getShape();
        final var graph = ctx.getGraph();
        if (shape == null || graph == null) {
            return List.of();
        }
        final var realised = graph.realisedSubgraph();
        return shape.getAbstractMethods().stream()
                .map(method -> renderMethod(realised, method))
                .collect(toUnmodifiableList());
    }

    private MethodImpl renderMethod(final RealisedSubgraph realised, final ExecutableElement method) {
        final var scope = new MethodScope(method);
        final var groupRoots = indexGroupRootsByNode(realised, scope);
        final var root = findReturnRoot(realised, scope);
        final var expression = render(root, realised, method, groupRoots);
        final var body =
                CodeBlock.builder().addStatement("return $L", expression).build();
        return new MethodImpl(method, body, Set.of());
    }

    private CodeBlock render(
            final Node node,
            final RealisedSubgraph realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots) {
        final var group = groupRoots.get(node);
        if (group != null) {
            return renderGroupTarget(group, realised, method, groupRoots);
        }
        final var inbound = inboundRealisedEdges(node, realised);
        if (inbound.isEmpty()) {
            return renderLeaf(node, method);
        }
        if (inbound.size() == SINGLE_EDGE) {
            return renderSingleEdge(inbound.get(0), realised, method, groupRoots);
        }
        throw new IllegalStateException(
                "node has " + inbound.size() + " inbound edges but is not a registered group root: " + node.id());
    }

    private CodeBlock renderSingleEdge(
            final Edge edge,
            final RealisedSubgraph realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots) {
        final var child = render(edge.getFrom(), realised, method, groupRoots);
        return edge.getCodegen()
                .orElseThrow(() -> new IllegalStateException("REALISED edge has no codegen: " + edge))
                .render(new VarNamesImpl(), IncomingValuesImpl.of(child));
    }

    private CodeBlock renderGroupTarget(
            final ExpansionGroup group,
            final RealisedSubgraph realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots) {
        final var byName = new LinkedHashMap<String, CodeBlock>();
        for (final var slot : group.getSlots()) {
            byName.put(slotName(slot), render(slot, realised, method, groupRoots));
        }
        final var positional = List.copyOf(byName.values());
        return group.getCodegen().render(new VarNamesImpl(), new IncomingValuesImpl(positional, Map.copyOf(byName)));
    }

    private CodeBlock renderLeaf(final Node node, final ExecutableElement method) {
        if (!(node.getLoc() instanceof SourceLocation)) {
            throw new IllegalStateException("leaf node is not a SourceLocation: " + node.id());
        }
        final var segments = ((SourceLocation) node.getLoc()).getPath().getSegments();
        if (segments.isEmpty()) {
            throw new IllegalStateException("SourceLocation has no segments: " + node.id());
        }
        final var paramName = segments.get(0);
        final var hasParam = method.getParameters().stream()
                .anyMatch(p -> p.getSimpleName().toString().equals(paramName));
        if (!hasParam) {
            throw new IllegalStateException(
                    "parameter '" + paramName + "' not found on method " + method.getSimpleName());
        }
        return CodeBlock.of("$N", paramName);
    }

    private Node findReturnRoot(final RealisedSubgraph realised, final Scope scope) {
        return realised.nodes()
                .filter(n -> n.getScope().equals(scope))
                .filter(BuildMethodBodies::isReturnRoot)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no return-root TargetLocation node in scope " + scope));
    }

    private static boolean isReturnRoot(final Node node) {
        return node.getLoc() instanceof TargetLocation
                && ((TargetLocation) node.getLoc()).getPath().getSegments().isEmpty();
    }

    private Map<Node, ExpansionGroup> indexGroupRootsByNode(final RealisedSubgraph realised, final Scope scope) {
        final var index = new HashMap<Node, ExpansionGroup>();
        realised.delegate()
                .groups()
                .filter(g -> g.getRoot().getScope().equals(scope))
                .filter(g -> !g.getStrategyClassFqn().startsWith(SEED_PACKAGE_PREFIX))
                .forEach(g -> index.putIfAbsent(g.getRoot(), g));
        return index;
    }

    private List<Edge> inboundRealisedEdges(final Node node, final RealisedSubgraph realised) {
        return realised.edges().filter(e -> e.getTo().equals(node)).collect(toUnmodifiableList());
    }

    private static String slotName(final Node slot) {
        if (slot.getLoc() instanceof TargetLocation) {
            final var segments = ((TargetLocation) slot.getLoc()).getPath().getSegments();
            if (!segments.isEmpty()) {
                return segments.get(segments.size() - 1);
            }
        }
        throw new IllegalStateException("cannot derive slot name from node: " + slot.id());
    }
}
