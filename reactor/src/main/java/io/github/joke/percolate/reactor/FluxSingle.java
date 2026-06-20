package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.TypeProbe;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * Same-paradigm reduction {@code Flux<T> → Mono<T>} via {@code flux.single()} (design D4): the <b>canonical</b>
 * single-element reduction. A developer reducing a stream to one value means exactly one element; {@code next}/
 * {@code last}/positional selections are distinct intents and are NOT auto-generated (write a manual converter). The
 * result stays reactive (a {@code Mono}); it never blocks.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxSingle implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!TypeProbe.isType(to, Reactors.MONO, ctx)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, Reactors.FLUX, Containers.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "single",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.single()", inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
