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
import reactor.core.publisher.Mono;

import static io.github.joke.percolate.reactor.Reactors.MONO;

// Downward interop bridge Optional<T> → Mono<T> via Mono.justOrEmpty (design D5): a target-driven conversion
// keyed on the concrete demanded Mono<T>, sourcing a concrete Optional<T> port. Entering the reactive world
// from a synchronous Optional never blocks.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class JustOrEmpty implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isType(to, MONO)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, "java.util.Optional", ctx.typeArgument(to, 0))
                .map(optional -> OperationSpec.of(
                        "justOrEmpty",
                        (OperationCodegen) inputs -> CodeBlock.of("$T.justOrEmpty($L)", Mono.class, inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("optional", optional, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }
}
