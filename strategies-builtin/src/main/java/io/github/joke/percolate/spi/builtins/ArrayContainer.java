package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Arrays;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The array sequence container. Opens via {@code Arrays.stream}; closes via {@code toArray()}. Arrays have no
 * synchronous single-element wrap, so {@link #wrap()} stays empty (inherited default) — kind stays a sequence because
 * {@link #collect()} is supplied. An array has no declared erasure ({@link #kindErasure} is empty); its kind is formed
 * by {@link #containerOf} as a reflective array type.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class ArrayContainer extends StreamContainer {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isArray(type);
    }

    @Override
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.arrayComponent(type);
    }

    @Override
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.empty();
    }

    @Override
    protected Optional<TypeMirror> containerOf(final TypeMirror element, final ResolveCtx ctx) {
        return Optional.of(ctx.arrayType(element));
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$T.stream($L)", Arrays.class, container));
    }

    @Override
    public Optional<UnarySnippet> collect() {
        return Optional.of(stream -> CodeBlock.of("$L$Z.toArray()", stream));
    }
}
