package io.github.joke.percolate.reactor;

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

// The generic, kind-free element transform over a Flux<T> — the reactive twin of StreamMap, keyed to
// reactor.core.publisher.Flux instead of java.util.stream.Stream (design D3/D5). Given a demand for Flux<B> it
// offers two scope-owning operations whose input port is the type-variable Flux<A> and whose child scope is the
// per-element plan:
//
//   map     (Flux<A> → Flux<B>, child A → B)       — flux.map(a -> …)
//   flatMap (Flux<A> → Flux<B>, child A → Flux<B>) — flux.flatMap(a -> …)
//
// It reads no candidate: A is grounded by the engine by matching the
// Flux<A> port against an in-scope source — directly when a Flux<X> source exists, or via MonoContainer's
// SourceProjection when only a Mono<X> source exists. The engine cannot tell flux.map from stream.map.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxMap implements ExpansionStrategy {

    private static final String FLUX = "reactor.core.publisher.Flux";
    private static final String SOURCE_ROLE = "flux";
    private static final ScopeCodegen MAP =
            (operand, var, body) -> CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body);
    private static final ScopeCodegen FLAT_MAP =
            (operand, var, body) -> CodeBlock.of("$L$Z.flatMap($N -> $L)", operand, var, body);

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, FLUX)) {
            return Stream.empty();
        }
        final var fluxErasure = ctx.typeElementNamed(FLUX);
        if (fluxErasure == null) {
            return Stream.empty();
        }
        final var elementOut = ctx.typeArgument(to, 0);
        final var template = PortType.app(fluxErasure, List.of(variable(0)));
        final var ports = List.of(new Port(SOURCE_ROLE, fluxErasure.asType(), NON_NULL, template));
        final var mapChild = lifted(variable(0), NON_NULL, elementOut, NON_NULL);
        final var flatMapChild = lifted(variable(0), NON_NULL, to, NON_NULL);
        return Stream.of(
                        mapping("map", MAP, CONTAINER, ports, to, NON_NULL, mapChild),
                        mapping("flatMap", FLAT_MAP, CONTAINER, ports, to, NON_NULL, flatMapChild))
                .map(Offer::of);
    }
}
