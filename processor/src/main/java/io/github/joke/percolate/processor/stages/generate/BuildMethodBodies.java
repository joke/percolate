package io.github.joke.percolate.processor.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.RealisedSubgraph;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Nullability;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

    private static final String SEED_PACKAGE_PREFIX = "io.github.joke.percolate.processor.stages.seed.";
    private static final int SINGLE_EDGE = 1;

    private final NullabilityResolver nullabilityResolver;

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
            final var name = slotName(slot);
            final var raw = render(slot, realised, method, groupRoots);
            byName.put(name, applyNullabilityContract(group, slot, name, raw));
        }
        final var positional = List.copyOf(byName.values());
        return group.getCodegen().render(new VarNamesImpl(), new IncomingValuesImpl(positional, Map.copyOf(byName)));
    }

    private CodeBlock applyNullabilityContract(
            final ExpansionGroup group, final Node slot, final String slotName, final CodeBlock expr) {
        final var producer = slot.getNullability().orElse(Nullability.UNKNOWN);
        if (producer != Nullability.NULLABLE) {
            return expr;
        }
        final var consumer = resolveConsumerContract(group, slot);
        if (consumer == Nullability.NON_NULL) {
            final var message = "source for slot '" + slotName + "' is null but target is non-null";
            return CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, expr, message);
        }
        return expr;
    }

    private Nullability resolveConsumerContract(final ExpansionGroup group, final Node slot) {
        final AnnotatedConstruct consumer = group.consumerContractFor(slot);
        if (consumer == null) {
            return Nullability.UNKNOWN;
        }
        final TypeMirror consumerType;
        final Element scope;
        if (consumer instanceof VariableElement) {
            final var ve = (VariableElement) consumer;
            consumerType = ve.asType();
            scope = ve;
        } else if (consumer instanceof Element) {
            final var e = (Element) consumer;
            consumerType = e.asType();
            scope = e;
        } else {
            return Nullability.UNKNOWN;
        }
        return nullabilityResolver.resolve(consumerType, scope);
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
        if (slot.getLoc() instanceof ElementLocation) {
            return ((ElementLocation) slot.getLoc()).getRole();
        }
        throw new IllegalStateException("cannot derive slot name from node: " + slot.id());
    }
}
