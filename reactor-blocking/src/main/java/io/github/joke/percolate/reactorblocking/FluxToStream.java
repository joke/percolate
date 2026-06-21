package io.github.joke.percolate.reactorblocking;

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
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * Upward async-to-sync crossing {@code Flux<T> → Stream<T>} via {@code flux.toStream()}: a distinct (lazily streaming,
 * not buffering) blocking bridge into the JDK {@code Stream} world — not redundant with
 * {@code collectList().block()} + {@code iterate} (that fully buffers first). Keyed on a target {@code Stream<T>} and
 * sourcing a {@code Flux<T>} through a <b>reuse-only</b> port. Weighted strictly above any non-blocking alternative;
 * shipped only in the opt-in {@code reactor-blocking} module.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxToStream implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Containers.isStream(to, ctx)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.FLUX, Containers.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "toStream",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.toStream()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
