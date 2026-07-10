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
 * Upward async-to-sync crossing {@code Flux<T> → T} via {@code flux.single().block()}: the canonical single-element
 * blocking reduction, keyed on a plain scalar {@code T} and sourcing a {@code Flux<T>} through a <b>reuse-only</b>
 * port. It is <b>partial</b> ({@code single()} fails unless the {@code Flux} has exactly one element), so the
 * element-preserving {@code collectList().block} / {@code toStream} (total) are preferred whenever a {@code List} or
 * {@code Stream} is demanded — totality dominates, so a multi-element target is never silently reduced to one.
 * Weighted strictly above any non-blocking alternative; shipped only in the opt-in {@code reactor-blocking} module.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxSingleBlock implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Blockings.isBlockableScalar(to, ctx)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.FLUX, to)
                .map(flux -> OperationSpec.ofPartial(
                        "single().block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.single()$Z.block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
