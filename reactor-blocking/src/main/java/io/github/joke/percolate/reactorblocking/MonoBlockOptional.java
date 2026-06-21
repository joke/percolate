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
 * Upward async-to-sync crossing {@code Mono<T> → Optional<T>} via {@code mono.blockOptional()}: the presence-preserving
 * blocking bridge, keyed on a target {@code Optional<T>} and sourcing a {@code Mono<T>} through a <b>reuse-only</b>
 * port. Weighted strictly above any non-blocking alternative; shipped only in the opt-in {@code reactor-blocking}
 * module.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MonoBlockOptional implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Containers.isOptional(to, ctx)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.MONO, Containers.typeArgument(to, 0))
                .map(mono -> OperationSpec.of(
                        "blockOptional",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.blockOptional()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("mono", mono, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
