package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import reactor.core.publisher.Flux;

import static java.util.Objects.requireNonNull;

// The reactor.core.publisher.Flux sequence container over the single shared reactive intermediate — which is
// Flux itself (design D1). Because the kind is the intermediate, .iterate() and .collect() would be identities
// (Flux<X> ← Flux<X>), so both are omitted (spike finding, design D2): a Flux<X> source unifies a generic
// Flux<A> map port directly, and the per-element transform is supplied by the external FluxMap. Only .wrap()
// (Flux.just) lifts a single scalar into a one-element Flux.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class FluxContainer extends Container {

    static final String FLUX = "reactor.core.publisher.Flux";

    @Override
    @VisibleForTesting
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isType(type, FLUX);
    }

    @Override
    @VisibleForTesting
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(FLUX));
    }

    @Override
    @VisibleForTesting
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return requireNonNull(
                ctx.typeElementNamed(FLUX), "reactor-core must be on the compile classpath when reactor is active");
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.just($L)", Flux.class, scalar));
    }
}
