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

// Same-paradigm reduction Flux<T> → Mono<List<T>> via flux.collectList() (design D4): a target-driven
// conversion keyed on a concrete Mono<List<T>>, sourcing a concrete Flux<T> port. The result stays in the
// reactive world (a Mono); it never blocks. The blocking Flux<T> → List<T> (which adds .block()) lives only in
// reactor-blocking.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class CollectList implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, MONO)) {
            return Stream.empty();
        }
        final var inner = ctx.typeArgument(to, 0);
        if (!ctx.isList(inner)) {
            return Stream.empty();
        }
        return declared(ctx, FLUX, ctx.typeArgument(inner, 0))
                .map(flux -> OperationSpec.of(
                        "collectList",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.collectList()", inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
