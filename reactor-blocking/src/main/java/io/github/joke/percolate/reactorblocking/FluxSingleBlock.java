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

// Upward async-to-sync crossing Flux<T> → T via flux.single().block(): the canonical single-element blocking
// reduction, keyed on a plain scalar T and sourcing a Flux<T> through a reuse-only port. It is partial
// (single() fails unless the Flux has exactly one element), so the element-preserving collectList().block /
// toStream (total) are preferred whenever a List or Stream is demanded — totality dominates, so a multi-element
// target is never silently reduced to one. Weighted strictly above any non-blocking alternative; shipped only
// in the opt-in reactor-blocking module.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxSingleBlock implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Blockings.isBlockableScalar(to, ctx)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, FLUX, to)
                .map(flux -> OperationSpec.ofPartial(
                        "single().block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.single()$Z.block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.byTypeOrDecline("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
