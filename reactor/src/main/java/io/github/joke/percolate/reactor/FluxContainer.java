package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import io.github.joke.percolate.spi.TypeProbe;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * The {@code reactor.core.publisher.Flux} sequence container over the single shared reactive intermediate — which is
 * {@code Flux} itself (design D1). Because the kind <b>is</b> the intermediate, {@link #iterate()} and {@link #collect()}
 * would be identities ({@code Flux<X> ← Flux<X>}), so both are omitted (spike finding, design D2): a {@code Flux<X>}
 * source unifies a generic {@code Flux<A>} map port directly, and the per-element transform is supplied by the external
 * {@link FluxMap}. Only {@link #wrap()} ({@code Flux.just}) lifts a single scalar into a one-element {@code Flux}.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class FluxContainer extends Container {

    static final String FLUX = "reactor.core.publisher.Flux";

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return TypeProbe.isType(type, FLUX, ctx);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.typeArgument(type, 0);
    }

    @Override
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.elements().getTypeElement(FLUX));
    }

    @Override
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return Objects.requireNonNull(
                ctx.elements().getTypeElement(FLUX),
                "reactor-core must be on the compile classpath when percolate-reactor is active");
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.just($L)", Flux.class, scalar));
    }
}
