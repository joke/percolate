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

import static io.github.joke.percolate.reactorblocking.Blockings.MONO;
import static io.github.joke.percolate.reactorblocking.Blockings.declared;
import static io.github.joke.percolate.reactorblocking.Blockings.isBlockableScalar;

// Upward async-to-sync crossing Mono<T> → T via mono.block(): a target-driven conversion keyed on a plain
// scalar T, sourcing a Mono<T> through a reuse-only port (the unwrap pattern) so it never mints a fresh Mono
// just to block it. The edge is weighted strictly above any non-blocking alternative, so a lazy reactive path
// always wins when one exists; blocking is chosen only when nothing else produces T. It is partial (block()
// returns null on an empty Mono), so the empty-safe blockOptional (total) is preferred whenever an Optional<T>
// is what's demanded — totality dominates. Shipped only in the opt-in reactor-blocking module — the engine
// never auto-invents it.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MonoBlock implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!isBlockableScalar(to, ctx)) {
            return Stream.empty();
        }
        return declared(ctx, MONO, to)
                .map(mono -> OperationSpec.ofPartial(
                        "block",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.block()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.byTypeOrDecline("mono", mono, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
