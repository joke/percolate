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
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

import static io.github.joke.percolate.reactorblocking.Blockings.FLUX;
import static io.github.joke.percolate.reactorblocking.Blockings.declared;

// Upward async-to-sync crossing Flux<T> → List<T> via flux.collectList().block(): the buffering blocking
// reduction, keyed on a target List<T> and sourcing a Flux<T> through a reuse-only port. Weighted strictly
// above any non-blocking alternative; shipped only in the opt-in reactor-blocking module.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxCollectListBlock implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isList(to)) {
            return Stream.empty();
        }
        return declared(ctx, FLUX, ctx.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "collectList().block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.collectList()$Z.block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.byTypeOrDecline("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
