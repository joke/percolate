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
import io.github.joke.percolate.spi.SourceProjection;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

// Upward async-to-sync crossing Flux<T> → Stream<T> via flux.toStream(): a distinct (lazily streaming, not
// buffering) blocking bridge into the JDK Stream world — not redundant with collectList().block() + iterate
// (that fully buffers first). Keyed on a target Stream<T> and sourcing a Flux<T> through a reuse-only port.
// Weighted strictly above any non-blocking alternative; shipped only in the opt-in reactor-blocking module.
//
// It is also the matching SourceProjection (Flux<X> → Stream<X>): a total grounding view so a JDK Stream<A>
// element-map port grounds its element type A against an in-scope reactive Flux<X> source — the producer/view
// pair that lets Flux<DTO> → List<DAO> with an element transform generate, mirroring how a Container bundles
// its project and expand. The view only widens the grounding-match set; the concrete Stream<X> is still
// produced by the weighted reuse-only toStream above.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class FluxToStream implements ExpansionStrategy, SourceProjection {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.isStream(to)) {
            return Stream.empty();
        }
        return Blockings.declared(ctx, Blockings.FLUX, ctx.typeArgument(to, 0))
                .map(flux -> OperationSpec.of(
                        "toStream",
                        (OperationCodegen) inputs -> CodeBlock.of("$L$Z.toStream()", inputs.single()),
                        Blockings.WEIGHT,
                        List.of(Port.byTypeOrDecline("flux", flux, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))
                .map(Offer::of)
                .stream();
    }

    @Override
    public Stream<TypeMirror> project(final TypeMirror source, final ResolveCtx ctx) {
        return Blockings.view(source, Blockings.FLUX, Blockings.STREAM, ctx);
    }
}
