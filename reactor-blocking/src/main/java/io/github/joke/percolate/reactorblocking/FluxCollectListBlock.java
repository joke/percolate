package io.github.joke.percolate.reactorblocking;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * Upward async-to-sync crossing {@code Flux<T> → List<T>} via {@code flux.collectList().block()}: the buffering
 * blocking reduction, keyed on a target {@code List<T>} and sourcing a {@code Flux<T>} through a <b>reuse-only</b>
 * port. Weighted strictly above any non-blocking alternative; shipped only in the opt-in {@code reactor-blocking}
 * module.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxCollectListBlock implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isList(to)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.FLUX, ctx.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "collectList().block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.collectList().block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
