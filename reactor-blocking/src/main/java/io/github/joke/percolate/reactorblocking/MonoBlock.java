package io.github.joke.percolate.reactorblocking;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
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
 * Upward async-to-sync crossing {@code Mono<T> → T} via {@code mono.block()}: a target-driven conversion keyed on a
 * plain scalar {@code T}, sourcing a {@code Mono<T>} through a <b>reuse-only</b> port (the {@code unwrap} pattern) so it
 * never mints a fresh {@code Mono} just to block it. The edge is weighted strictly above any non-blocking alternative,
 * so a lazy reactive path always wins when one exists; blocking is chosen only when nothing else produces {@code T}.
 * It is <b>partial</b> ({@code block()} returns {@code null} on an empty {@code Mono}), so the empty-safe
 * {@code blockOptional} (total) is preferred whenever an {@code Optional<T>} is what's demanded — totality dominates.
 * Shipped only in the opt-in {@code reactor-blocking} module — the engine never auto-invents it.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MonoBlock implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Blockings.isBlockableScalar(to, ctx)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.MONO, to)
                .map(mono -> OperationSpec.ofPartial(
                        "block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("mono", mono, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
