package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Arrays;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The array sequence container. Opens via {@code Arrays.stream}; closes via {@code toArray()}. Arrays have no
 * synchronous single-element wrap, so {@link #wrap()} stays empty (inherited default) — kind stays a sequence
 * because {@link #collect()} is supplied.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class ArrayContainer extends Container {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return Containers.isArray(type);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.arrayComponentType(type);
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$T.stream($L)", Arrays.class, container));
    }

    @Override
    public Optional<UnarySnippet> collect() {
        return Optional.of(stream -> CodeBlock.of("$L.toArray()", stream));
    }
}
