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
import io.github.joke.percolate.spi.SourceProjection;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Upward async-to-sync crossing {@code Flux<T> → Stream<T>} via {@code flux.toStream()}: a distinct (lazily streaming,
 * not buffering) blocking bridge into the JDK {@code Stream} world — not redundant with
 * {@code collectList().block()} + {@code iterate} (that fully buffers first). Keyed on a target {@code Stream<T>} and
 * sourcing a {@code Flux<T>} through a <b>reuse-only</b> port. Weighted strictly above any non-blocking alternative;
 * shipped only in the opt-in {@code reactor-blocking} module.
 *
 * <p>It is also the matching <b>{@link SourceProjection}</b> ({@code Flux<X> → Stream<X>}): a total grounding view so a
 * JDK {@code Stream<A>} element-map port grounds its element type {@code A} against an in-scope reactive {@code Flux<X>}
 * source — the producer/view pair that lets {@code Flux<DTO> → List<DAO>} with an element transform generate, mirroring
 * how a {@code Container} bundles its {@code project} and {@code expand}. The view only widens the grounding-match set;
 * the concrete {@code Stream<X>} is still produced by the weighted reuse-only {@code toStream} above.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class FluxToStream implements ExpansionStrategy, SourceProjection {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isStream(to)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.FLUX, ctx.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "toStream",
                        (OperationCodegen) inputs -> CodeBlock.of("$L.toStream()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.reuse("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .stream();
    }

    @Override
    public Stream<TypeMirror> project(final TypeMirror source, final ResolveCtx ctx) {
        return Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx);
    }
}
