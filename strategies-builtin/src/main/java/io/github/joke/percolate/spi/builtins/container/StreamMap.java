package io.github.joke.percolate.spi.builtins.container;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

import static io.github.joke.percolate.spi.ChildScopeSpec.lifted;
import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.OperationSpec.mapping;
import static io.github.joke.percolate.spi.PortType.variable;
import static io.github.joke.percolate.spi.Weights.CONTAINER;

// The generic, kind-free element transform over a Stream<T> (design D3/D7) — a functor lift: given a demand for
// Stream<B> it offers two scope-owning operations whose input port is the type-variable Stream<A> (a
// PortType#app App over PortType#variable Var 0) and whose child scope is the per-element plan:
//
//   map     (Stream<A> → Stream<B>, child A → B)         — stream.map(a -> …)
//   flatMap (Stream<A> → Stream<B>, child A → Stream<B>) — stream.flatMap(a -> …), which is how a wrapper
//           element (its iterate yields a 0-or-1 stream) is flattened / dropped
//
// It reads no candidate: the element type A is grounded by the engine (design D2) by
// matching the Stream<A> port against an in-scope concrete source — directly when a Stream<X> source exists, or
// via a container's io.github.joke.percolate.spi.SourceProjection when only a List<X>/Optional<X>/… source
// exists (D8). It names no container kind beyond its own Stream; the grounded Stream<A> port is produced
// target-driven by a container's own iterate, so cross-kind composition and flatten emerge from the graph
// rather than from any multi-kind composer.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class StreamMap implements ExpansionStrategy {

    private static final String SOURCE_ROLE = "stream";
    private static final ScopeCodegen MAP =
            (operand, var, body) -> CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body);
    private static final ScopeCodegen FLAT_MAP =
            (operand, var, body) -> CodeBlock.of("$L$Z.flatMap($N -> $L)", operand, var, body);

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isStream(to)) {
            return Stream.empty();
        }
        final var streamErasure = ctx.typeElementNamed("java.util.stream.Stream");
        if (streamErasure == null) {
            return Stream.empty();
        }
        final var elementOut = ctx.typeArgument(to, 0);
        final var template = PortType.app(streamErasure, List.of(variable(0)));
        final var ports = List.of(new Port(SOURCE_ROLE, streamErasure.asType(), NON_NULL, template));
        final var mapChild = lifted(variable(0), NON_NULL, elementOut, NON_NULL);
        final var flatMapChild = lifted(variable(0), NON_NULL, to, NON_NULL);
        return Stream.of(
                        mapping("map", MAP, CONTAINER, ports, to, NON_NULL, mapChild),
                        mapping("flatMap", FLAT_MAP, CONTAINER, ports, to, NON_NULL, flatMapChild))
                .map(Offer::of);
    }
}
