package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The {@code reactor.core.publisher.Mono} presence container over the shared reactive intermediate {@code Flux} (design
 * D1). Like {@code OptionalContainer} it supplies no {@code collect}; that absence is what makes its kind a presence
 * wrapper. {@link #iterate()} opens a {@code Mono<X>} into a {@code Flux<X>} ({@code Mono.flux()}) — the shared
 * intermediate — which is how a {@code Mono} source feeds the generic {@link FluxMap} (its {@link #project} projects the
 * same {@code Mono<X> → Flux<X>}). {@link #mapPresence()} maps the wrapped value ({@code mono.map}) as a same-kind
 * functor lift; {@link #wrap()} lifts a non-null scalar via {@code Mono.just}.
 *
 * <p>It supplies <b>no</b> {@code unwrap}: collapsing a {@code Mono} to a scalar is {@code block()} — an async-to-sync
 * crossing that blocks a thread. That edge lives only in the opt-in {@code reactor-blocking} module, never here (design
 * D3, the boundary-direction rule), so the engine reports "no producer" for {@code Mono<T> → T} unless blocking is
 * explicitly enabled.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class MonoContainer extends Container {

    static final String MONO = "reactor.core.publisher.Mono";
    static final String FLUX = "reactor.core.publisher.Flux";

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isType(type, MONO);
    }

    @Override
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(MONO));
    }

    @Override
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return Objects.requireNonNull(
                ctx.typeElementNamed(FLUX),
                "reactor-core must be on the compile classpath when percolate-reactor is active");
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$L$Z.flux()", container));
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.just($L)", Mono.class, scalar));
    }

    @Override
    public Optional<ScopeCodegen> mapPresence() {
        return Optional.of((operand, var, body) -> CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body));
    }
}
