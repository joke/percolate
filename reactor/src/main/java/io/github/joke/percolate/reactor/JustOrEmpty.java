package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.TypeProbe;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Downward interop bridge {@code Optional<T> → Mono<T>} via {@code Mono.justOrEmpty} (design D5): a target-driven
 * conversion keyed on the concrete demanded {@code Mono<T>}, sourcing a concrete {@code Optional<T>} port. Entering the
 * reactive world from a synchronous {@code Optional} never blocks.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class JustOrEmpty implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!TypeProbe.isType(to, Reactors.MONO, ctx)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, "java.util.Optional", Containers.typeArgument(to, 0))
                .map(optional -> OperationSpec.of(
                        "justOrEmpty",
                        (OperationCodegen) inputs -> CodeBlock.of("$T.justOrEmpty($L)", Mono.class, inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("optional", optional, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
