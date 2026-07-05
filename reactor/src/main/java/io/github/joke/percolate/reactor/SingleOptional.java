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
 * Same-paradigm reduction {@code Mono<T> → Mono<Optional<T>>} via {@code mono.singleOptional()} (design D4): a
 * target-driven conversion keyed on a concrete {@code Mono<Optional<T>>}, sourcing a concrete {@code Mono<T>} port. It
 * surfaces emptiness as an {@code Optional} while staying reactive; it never blocks.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class SingleOptional implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, Reactors.MONO)) {
            return Stream.empty();
        }
        final var inner = ctx.typeArgument(to, 0);
        if (!ctx.isOptional(inner)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, Reactors.MONO, ctx.typeArgument(inner, 0))
                .map(mono -> OperationSpec.of(
                        "singleOptional",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.singleOptional()", inputs.single()),
                        Weights.STEP,
                        List.of(new Port("mono", mono, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
