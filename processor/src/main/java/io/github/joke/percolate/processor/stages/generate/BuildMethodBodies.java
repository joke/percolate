package io.github.joke.percolate.processor.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.ElementLocation;
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
import io.github.joke.percolate.spi.Slot;
import io.github.joke.percolate.spi.StreamOps;
import io.github.joke.percolate.spi.WrapperCodegen;
import jakarta.inject.Inject;
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

/**
 * Composes each abstract method body by recursing over the plan view's {@link Node}s and {@link Edge}s only — it
 * never reads an {@code ExpansionGroup}. The n-ary producer (constructor / multi-arg call) is reconstructed from
 * the output node's fan-in: its incoming REALISED plan edges, which share a producer {@link EdgeCodegen}. The
 * consumer nullability contract is read from each operand edge's {@link Slot}.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

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
        final var root = findReturnRoot(realised, scope);
        final var expression = render(root, realised, method, new VarGen());
        final var body = CodeBlock.builder()
                .addStatement("return $L", expression.getBlock())
                .build();
        return new MethodImpl(method, body, Set.of());
    }

    /**
     * Renders {@code node} as an expression, threading {@code isStream} and, when streaming, the owning
     * {@link StreamOps} handle. Dispatches purely on the node's inbound plan edges: no inbound edge is a leaf; a
     * single container edge is woven; a single scalar/producer edge or a multi-edge fan-in renders the producer
     * codegen carried by the edge(s).
     */
    private Rendered render(
            final Node node, final PlanView realised, final ExecutableElement method, final VarGen varGen) {
        final var inbound = inboundRealisedEdges(node, realised);
        if (inbound.isEmpty()) {
            return Rendered.scalar(renderLeaf(node, method));
        }
        if (inbound.size() == SINGLE_EDGE && isContainerEdge(inbound.get(0))) {
            return renderContainerEdge(inbound.get(0), realised, method, varGen);
        }
        return renderProducer(inbound, realised, method, varGen);
    }

    private static boolean isContainerEdge(final Edge edge) {
        return edge.getCodegen().map(c -> c instanceof StreamOps).orElse(false);
    }

    /**
     * Assembly fan-in / scalar producer case. The operands are the {@code from} nodes of {@code node}'s incoming
     * REALISED plan edges, which share one producer {@link EdgeCodegen}; each operand is keyed by the slot-name
     * rule and its nullability contract is read off the operand edge's {@link Slot}. A single-operand producer
     * whose operand renders as an open stream applies the producer per element (a scalar bridge such as a
     * conversion / method call); otherwise the producer renders once inline.
     */
    private Rendered renderProducer(
            final List<Edge> inbound, final PlanView realised, final ExecutableElement method, final VarGen varGen) {
        final var producer = (EdgeCodegen) inbound.get(0)
                .getCodegen()
                .orElseThrow(() -> new IllegalStateException("REALISED edge has no codegen: " + inbound.get(0)));
        if (inbound.size() == SINGLE_EDGE) {
            final var edge = inbound.get(0);
            final var source = realised.getEdgeSource(edge);
            final var child = render(source, realised, method, varGen);
            final var name = slotName(source);
            if (child.isStream()) {
                final var handle = child.getStreamHandle()
                        .orElseThrow(
                                () -> new IllegalStateException("stream operand has no container handle: " + edge));
                final var var = varGen.fresh();
                final var body = producer.render(new VarNamesImpl(), incoming(name, CodeBlock.of("$N", var)));
                return new Rendered(handle.mapElements(child.getBlock(), var, body), true, child.getStreamHandle());
            }
            final var operand = applyNullabilityContract(realised, edge, name, child.getBlock());
            return Rendered.scalar(producer.render(new VarNamesImpl(), incoming(name, operand)));
        }
        final var byName = new LinkedHashMap<String, CodeBlock>();
        for (final var edge : inbound) {
            final var source = realised.getEdgeSource(edge);
            final var name = slotName(source);
            final var child = render(source, realised, method, varGen);
            byName.put(name, applyNullabilityContract(realised, edge, name, child.getBlock()));
        }
        final var positional = List.copyOf(byName.values());
        return Rendered.scalar(
                producer.render(new VarNamesImpl(), new IncomingValuesImpl(positional, Map.copyOf(byName))));
    }

    private static IncomingValuesImpl incoming(final String name, final CodeBlock value) {
        return new IncomingValuesImpl(List.of(value), Map.of(name, value));
    }

    /**
     * Container single-edge case. The provider + the edge's {@link io.github.joke.percolate.spi.ElementScope}
     * (plus the child's {@code isStream}) select the operation; every {@code CodeBlock} comes from the provider.
     */
    private Rendered renderContainerEdge(
            final Edge edge, final PlanView realised, final ExecutableElement method, final VarGen varGen) {
        final var provider = (StreamOps) edge.getCodegen().orElseThrow();
        final var child = render(realised.getEdgeSource(edge), realised, method, varGen);
        final var scope = edge.getElementScope()
                .orElseThrow(() -> new IllegalStateException("container provider on a scope-preserving edge: " + edge));
        switch (scope) {
            case ENTERING:
                return renderEntering(realised, edge, provider, child, varGen);
            case EXITING:
                // collect closes a stream back into a container — a sequence terminal only.
                return Rendered.scalar(((ContainerCodegen) provider).collect(child.getBlock()));
        }
        throw new IllegalStateException("unknown element scope: " + scope);
    }

    private Rendered renderEntering(
            final PlanView realised,
            final Edge edge,
            final StreamOps provider,
            final Rendered child,
            final VarGen varGen) {
        if (provider instanceof WrapperCodegen) {
            final var wrapper = (WrapperCodegen) provider;
            if (!child.isStream()) {
                // Top-level wrapper: collapse to a scalar under the target's nullability.
                final var nullability =
                        realised.getEdgeTarget(edge).getNullability().orElse(Nullability.UNKNOWN);
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

    /**
     * Nullability-aware wiring (the assembly fan-in operand case): compares the operand node's producer-stamped
     * nullability against the consumer contract derived from the operand <strong>edge's</strong> {@link Slot}
     * {@code producedFrom}. A {@code NULLABLE → NON_NULL} crossing wraps the operand in {@code requireNonNull};
     * all other combinations emit the operand unchanged. The contract is never read from an {@code ExpansionGroup}.
     */
    private CodeBlock applyNullabilityContract(
            final PlanView realised, final Edge edge, final String slotName, final CodeBlock expr) {
        final var producer = realised.getEdgeSource(edge).getNullability().orElse(Nullability.UNKNOWN);
        if (producer != Nullability.NULLABLE) {
            return expr;
        }
        if (resolveConsumerContract(edge) == Nullability.NON_NULL) {
            final var message = "source for slot '" + slotName + "' is null but target is non-null";
            return CodeBlock.of("$T.requireNonNull($L, $S)", Objects.class, expr, message);
        }
        return expr;
    }

    private Nullability resolveConsumerContract(final Edge edge) {
        final AnnotatedConstruct consumer =
                edge.getConsumerSlot().map(Slot::getProducedFrom).orElse(null);
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

    private List<Edge> inboundRealisedEdges(final Node node, final PlanView realised) {
        return realised.edges()
                .filter(e -> realised.getEdgeTarget(e).equals(node))
                .collect(toUnmodifiableList());
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
     * stream and, if so, the {@link StreamOps} handle that owns the stream (consulted by a scalar element
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
