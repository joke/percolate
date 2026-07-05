package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * Same-paradigm reduction {@code Flux<T> → Mono<List<T>>} via {@code flux.collectList()} (design D4): a target-driven
 * conversion keyed on a concrete {@code Mono<List<T>>}, sourcing a concrete {@code Flux<T>} port. The result stays in
 * the reactive world (a {@code Mono}); it never blocks. The blocking {@code Flux<T> → List<T>} (which adds
 * {@code .block()}) lives only in {@code reactor-blocking}.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class CollectList implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, Reactors.MONO)) {
            return Stream.empty();
        }
        final var inner = ctx.typeArgument(to, 0);
        if (!ctx.isList(inner)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, Reactors.FLUX, ctx.typeArgument(inner, 0))
                .map(flux -> OperationSpec.of(
                        "collectList",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.collectList()", inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
