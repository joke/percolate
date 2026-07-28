package io.github.joke.percolate.reactorblocking;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

// Upward async-to-sync crossing Mono<T> → Optional<T> via mono.blockOptional(): the presence-preserving
// blocking bridge, keyed on a target Optional<T> and sourcing a Mono<T> through a reuse-only port. Weighted
// strictly above any non-blocking alternative; shipped only in the opt-in reactor-blocking module.
//
// It is also the matching SourceProjection (Mono<X> → Optional<X>): a total grounding view (projected from
// blockOptional, never the partial block/single().block) so an Optional<A> presence-map port grounds its
// element type A against an in-scope reactive Mono<X> source. The view only widens the grounding-match set; the
// concrete Optional<X> is still produced by the weighted reuse-only blockOptional above.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class MonoBlockOptional implements ExpansionStrategy, SourceProjection {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isOptional(to)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.MONO, ctx.typeArgument(to, 0))
                .map(mono -> OperationSpec.of(
                        "blockOptional",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.blockOptional()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.byTypeOrDecline("mono", mono, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }

    @Override
    public Stream<TypeMirror> project(final TypeMirror source, final ResolveCtx ctx) {
        return Blockings.view(source, Blockings.MONO, Blockings.OPTIONAL, ctx);
    }
}
