package io.github.joke.percolate.reactor;

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
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

import static io.github.joke.percolate.reactor.Reactors.FLUX;
import static io.github.joke.percolate.reactor.Reactors.MONO;
import static io.github.joke.percolate.reactor.Reactors.declared;

// Same-paradigm reduction Flux<T> → Mono<T> via flux.single() (design D4): the canonical single-element
// reduction. A developer reducing a stream to one value means exactly one element; next/ last/positional
// selections are distinct intents and are NOT auto-generated (write a manual converter). The result stays
// reactive (a Mono); it never blocks.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxSingle implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, MONO)) {
            return Stream.empty();
        }
        return declared(ctx, FLUX, ctx.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "single",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.single()", inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
