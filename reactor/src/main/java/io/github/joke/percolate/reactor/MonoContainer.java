package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
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
import org.jetbrains.annotations.VisibleForTesting;
import reactor.core.publisher.Mono;

// The reactor.core.publisher.Mono presence container over the shared reactive intermediate Flux (design D1).
// Like OptionalContainer it supplies no collect; that absence is what makes its kind a presence wrapper.
// .iterate() opens a Mono<X> into a Flux<X> (Mono.flux()) — the shared intermediate — which is how a Mono
// source feeds the generic FluxMap (its .project projects the same Mono<X> → Flux<X>). .mapPresence() maps the
// wrapped value (mono.map) as a same-kind functor lift; .wrap() lifts a non-null scalar via Mono.just.
//
// It supplies no unwrap: collapsing a Mono to a scalar is block() — an async-to-sync crossing that blocks a
// thread. That edge lives only in the opt-in reactor-blocking module, never here (design D3, the boundary-
// direction rule), so the engine reports "no producer" for Mono<T> → T unless blocking is explicitly enabled.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class MonoContainer extends Container {

    static final String MONO = "reactor.core.publisher.Mono";
    static final String FLUX = "reactor.core.publisher.Flux";

    @Override
    @VisibleForTesting
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isType(type, MONO);
    }

    @Override
    @VisibleForTesting
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(MONO));
    }

    @Override
    @VisibleForTesting
    protected TypeElement intermediateErasure(final ResolveCtx ctx) {
        return Objects.requireNonNull(
                ctx.typeElementNamed(FLUX), "reactor-core must be on the compile classpath when reactor is active");
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
