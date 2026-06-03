package io.github.joke.percolate.processor.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.PlanView;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.ContainerCodegen;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.StreamOps;
import io.github.joke.percolate.spi.WrapperCodegen;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@SuppressWarnings("PMD.GodClass")
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
        final var realised = graph.planView();
        return shape.getAbstractMethods().stream()
                .map(method -> renderMethod(realised, method))
                .collect(toUnmodifiableList());
    }

    private MethodImpl renderMethod(final PlanView realised, final ExecutableElement method) {
        final var scope = new MethodScope(method);
        final var groupRoots = indexGroupRootsByNode(realised, scope);
        final var root = findReturnRoot(realised, scope);
        final var expression = render(root, realised, method, groupRoots, new VarGen());
        final var body = CodeBlock.builder()
                .addStatement("return $L", expression.getBlock())
                .build();
        return new MethodImpl(method, body, Set.of());
    }

    /**
     * Renders {@code node} as an expression, threading {@code isStream} — whether the rendered expression is an
     * open element stream — and, when streaming, the {@link ContainerCodegen} handle that owns the stream (so a
     * scalar element transform can ask it for {@code mapElements}). Container hops are intercepted before the
     * group/leaf cases: a node whose single inbound edge carries a container provider is woven per the rules.
     */
    private Rendered render(
            final Node node,
            final PlanView realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots,
            final VarGen varGen) {
        final var inbound = inboundRealisedEdges(node, realised);
        if (inbound.size() == SINGLE_EDGE && isContainerEdge(inbound.get(0))) {
            return renderContainerEdge(inbound.get(0), realised, method, groupRoots, varGen);
        }
        final var group = groupRoots.get(node);
        if (group != null) {
            return renderGroupTarget(group, realised, method, groupRoots, varGen);
        }
        if (inbound.isEmpty()) {
            return Rendered.scalar(renderLeaf(node, method));
        }
        if (inbound.size() == SINGLE_EDGE) {
            return renderScalarEdge(inbound.get(0), realised, method, groupRoots, varGen);
        }
        throw new IllegalStateException(
                "node has " + inbound.size() + " inbound edges but is not a registered group root: " + node.id());
    }

    private static boolean isContainerEdge(final Edge edge) {
        return edge.getCodegen().map(c -> c instanceof StreamOps).orElse(false);
    }

    /** Scalar single-edge case. Inline when the child is not a stream; per-element {@code mapElements} when it is. */
    private Rendered renderScalarEdge(
            final Edge edge,
            final PlanView realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots,
            final VarGen varGen) {
        final var child = render(edge.getFrom(), realised, method, groupRoots, varGen);
        final var scalar = (EdgeCodegen)
                edge.getCodegen().orElseThrow(() -> new IllegalStateException("REALISED edge has no codegen: " + edge));
        if (!child.isStream()) {
            return Rendered.scalar(scalar.render(new VarNamesImpl(), IncomingValuesImpl.of(child.getBlock())));
        }
        final var handle = child.getStreamHandle()
                .orElseThrow(() -> new IllegalStateException("stream child has no container handle: " + edge));
        final var var = varGen.fresh();
        final var body = scalar.render(new VarNamesImpl(), IncomingValuesImpl.of(CodeBlock.of("$N", var)));
        return new Rendered(handle.mapElements(child.getBlock(), var, body), true, child.getStreamHandle());
    }

    /**
     * Container single-edge case. The provider + the edge's {@link io.github.joke.percolate.spi.ElementScope}
     * (plus the child's {@code isStream}) select the operation; every {@code CodeBlock} comes from the provider.
     */
    private Rendered renderContainerEdge(
            final Edge edge,
            final PlanView realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots,
            final VarGen varGen) {
        final var provider = (StreamOps) edge.getCodegen().orElseThrow();
        final var child = render(edge.getFrom(), realised, method, groupRoots, varGen);
        final var scope = edge.getElementScope()
                .orElseThrow(() -> new IllegalStateException("container provider on a scope-preserving edge: " + edge));
        switch (scope) {
            case ENTERING:
                return renderEntering(edge, provider, child, varGen);
            case EXITING:
                // collect closes a stream back into a container — a sequence terminal only.
                return Rendered.scalar(((ContainerCodegen) provider).collect(child.getBlock()));
        }
        throw new IllegalStateException("unknown element scope: " + scope);
    }

    private Rendered renderEntering(
            final Edge edge, final StreamOps provider, final Rendered child, final VarGen varGen) {
        if (provider instanceof WrapperCodegen) {
            final var wrapper = (WrapperCodegen) provider;
            if (!child.isStream()) {
                // Top-level wrapper: collapse to a scalar under the target's nullability.
                final var nullability = edge.getTo().getNullability().orElse(Nullability.UNKNOWN);
                return Rendered.scalar(wrapper.unwrap(child.getBlock(), nullability));
            }
            // Wrapper inside a stream: flat-map its element stream, dropping empties (FilterPresent).
            final var var = varGen.fresh();
            final var inner = provider.iterate(CodeBlock.of("$N", var));
            return new Rendered(provider.flatMapElements(child.getBlock(), var, inner), true, Optional.of(provider));
        }
        // Sequence: open an element stream.
        return new Rendered(provider.iterate(child.getBlock()), true, Optional.of(provider));
    }

    private Rendered renderGroupTarget(
            final ExpansionGroup group,
            final PlanView realised,
            final ExecutableElement method,
            final Map<Node, ExpansionGroup> groupRoots,
            final VarGen varGen) {
        final var slots = group.getSlots();
        final var rendered = slots.stream()
                .map(slot -> render(slot, realised, method, groupRoots, varGen))
                .collect(toUnmodifiableList());

        // A single-slot group fed by an open element stream (a scalar bridge sub-group such as a conversion or
        // method call) applies its codegen per element: map(v -> codegen(v)), staying a stream. Multi-slot groups
        // (ConstructorCall) and non-streaming single slots assemble their inputs directly (isStream = false).
        if (slots.size() == SINGLE_EDGE && rendered.get(0).isStream()) {
            final var child = rendered.get(0);
            final var handle = child.getStreamHandle()
                    .orElseThrow(() -> new IllegalStateException("stream slot has no container handle: "
                            + group.getRoot().id()));
            final var var = varGen.fresh();
            final var body =
                    group.getCodegen().render(new VarNamesImpl(), IncomingValuesImpl.of(CodeBlock.of("$N", var)));
            return new Rendered(handle.mapElements(child.getBlock(), var, body), true, child.getStreamHandle());
        }

        final var byName = new LinkedHashMap<String, CodeBlock>();
        for (var i = 0; i < slots.size(); i++) {
            final var slot = slots.get(i);
            final var name = slotName(slot);
            byName.put(
                    name,
                    applyNullabilityContract(group, slot, name, rendered.get(i).getBlock()));
        }
        final var positional = List.copyOf(byName.values());
        return Rendered.scalar(
                group.getCodegen().render(new VarNamesImpl(), new IncomingValuesImpl(positional, Map.copyOf(byName))));
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

    private Node findReturnRoot(final PlanView realised, final Scope scope) {
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

    private Map<Node, ExpansionGroup> indexGroupRootsByNode(final PlanView realised, final Scope scope) {
        final var index = new HashMap<Node, ExpansionGroup>();
        realised.groups()
                .filter(g -> g.getRoot().getScope().equals(scope))
                .filter(g -> !g.getStrategyClassFqn().startsWith(SEED_PACKAGE_PREFIX))
                .forEach(g -> index.put(g.getRoot(), g));
        return index;
    }

    private List<Edge> inboundRealisedEdges(final Node node, final PlanView realised) {
        return realised.edges().filter(e -> e.getTo().equals(node)).collect(toUnmodifiableList());
    }

    private static String slotName(final Node slot) {
        if (slot.getLoc() instanceof TargetLocation) {
            final var segments = ((TargetLocation) slot.getLoc()).getPath().getSegments();
            if (!segments.isEmpty()) {
                return segments.get(segments.size() - 1);
            }
        }
        if (slot.getLoc() instanceof SourceLocation) {
            final var segments = ((SourceLocation) slot.getLoc()).getPath().getSegments();
            if (!segments.isEmpty()) {
                return segments.get(segments.size() - 1);
            }
        }
        if (slot.getLoc() instanceof ElementLocation) {
            return ((ElementLocation) slot.getLoc()).getRole();
        }
        throw new IllegalStateException("cannot derive slot name from node: " + slot.id());
    }

    /**
     * A rendered expression plus the cross-hop facts that ride up the recursion: whether it is an open element
     * stream and, if so, the {@link ContainerCodegen} handle that owns the stream (consulted by a scalar element
     * transform for {@code mapElements}).
     */
    @lombok.Value
    private static class Rendered {
        CodeBlock block;
        boolean isStream;
        Optional<StreamOps> streamHandle;

        static Rendered scalar(final CodeBlock block) {
            return new Rendered(block, false, Optional.empty());
        }
    }

    /** Generates fresh, deterministic lambda-parameter names per rendered method. */
    private static final class VarGen {
        private int next;

        String fresh() {
            final var current = next;
            next += 1;
            return "v" + current;
        }
    }
}
