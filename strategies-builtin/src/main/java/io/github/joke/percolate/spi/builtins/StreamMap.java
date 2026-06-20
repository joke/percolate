package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ChildScopeSpec;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * The generic, kind-free element transform over a {@code Stream<T>} (design D3/D7) — a <b>functor lift</b>: given a
 * demand for {@code Stream<B>} it offers two scope-owning operations whose input port is the type-variable
 * {@code Stream<A>} (a {@link PortType#app App} over {@link PortType#variable Var 0}) and whose child scope is the
 * per-element plan:
 *
 * <ul>
 *   <li><b>map</b> ({@code Stream<A> → Stream<B>}, child {@code A → B}) — {@code stream.map(a -> …)};</li>
 *   <li><b>flatMap</b> ({@code Stream<A> → Stream<B>}, child {@code A → Stream<B>}) — {@code stream.flatMap(a -> …)},
 *       which is how a wrapper element (its {@code iterate} yields a 0-or-1 stream) is flattened / dropped.</li>
 * </ul>
 *
 * <p>It reads no candidate: the element type {@code A} is grounded by the engine (design D2) by <em>matching</em> the
 * {@code Stream<A>} port against an in-scope concrete source — directly when a {@code Stream<X>} source exists, or via
 * a container's {@link io.github.joke.percolate.spi.SourceProjection} when only a {@code List<X>}/{@code Optional<X>}/…
 * source exists (D8). It names no container kind beyond its own {@code Stream}; the grounded {@code Stream<A>} port is
 * produced target-driven by a container's own {@code iterate}, so cross-kind composition and flatten emerge from the
 * graph rather than from any multi-kind composer.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class StreamMap implements ExpansionStrategy {

    private static final String SOURCE_ROLE = "stream";
    private static final ScopeCodegen MAP =
            (operand, var, body) -> CodeBlock.of("$L.map($N -> $L)", operand, var, body);
    private static final ScopeCodegen FLAT_MAP =
            (operand, var, body) -> CodeBlock.of("$L.flatMap($N -> $L)", operand, var, body);

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Containers.isStream(to, ctx)) {
            return Stream.empty();
        }
        final var streamErasure = ctx.elements().getTypeElement("java.util.stream.Stream");
        if (streamErasure == null) {
            return Stream.empty();
        }
        final var elementOut = Containers.typeArgument(to, 0);
        final var template = PortType.app(streamErasure, List.of(PortType.variable(0)));
        final var ports = List.of(new Port(SOURCE_ROLE, streamErasure.asType(), Nullability.NON_NULL, template));
        final var mapChild =
                ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
        final var flatMapChild =
                ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, to, Nullability.NON_NULL);
        return Stream.of(
                OperationSpec.mapping("map", MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, mapChild),
                OperationSpec.mapping(
                        "flatMap", FLAT_MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, flatMapChild));
    }
}
