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
import reactor.core.publisher.Flux;

/**
 * Downward interop bridge {@code Stream<T> → Flux<T>} via {@code Flux.fromStream} (design D5): a target-driven
 * conversion keyed on the concrete demanded {@code Flux<T>}, sourcing a concrete {@code Stream<T>} port. The JDK
 * collection containers feed that {@code Stream<T>} through the shared {@code java.util.stream.Stream} intermediate
 * (e.g. a {@code List<DTO>} → {@code Stream<DAO>}), so any JDK collection bridges into the reactive world without
 * blocking.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxFromStream implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!TypeProbe.isType(to, Reactors.FLUX, ctx)) {
            return Stream.empty();
        }
        return Reactors.declared(ctx, "java.util.stream.Stream", Containers.typeArgument(to, 0))
                .map(stream -> OperationSpec.of(
                        "fromStream",
                        (OperationCodegen) inputs -> CodeBlock.of("$T.fromStream($L)", Flux.class, inputs.single()),
                        Weights.CONTAINER,
                        List.of(new Port("stream", stream, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }
}
