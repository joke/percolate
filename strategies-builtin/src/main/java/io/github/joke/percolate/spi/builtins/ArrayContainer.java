package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SequenceContainer;
import java.util.Arrays;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The array sequence container. Opens via {@code Arrays.stream}; closes via {@code toArray()}. Arrays have no
 * synchronous single-element wrap, so {@link #singleElementWrap()} stays empty (inherited default).
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ArrayContainer extends SequenceContainer {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return Containers.isArray(type);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.arrayComponentType(type);
    }

    @Override
    public CodeBlock iterate(final CodeBlock container) {
        return CodeBlock.of("$T.stream($L)", Arrays.class, container);
    }

    @Override
    public CodeBlock mapElements(final CodeBlock stream, final String var, final CodeBlock body) {
        return CodeBlock.of("$L.map($N -> $L)", stream, var, body);
    }

    @Override
    public CodeBlock flatMapElements(final CodeBlock stream, final String var, final CodeBlock inner) {
        return CodeBlock.of("$L.flatMap($N -> $L)", stream, var, inner);
    }

    @Override
    public CodeBlock collect(final CodeBlock stream) {
        return CodeBlock.of("$L.toArray()", stream);
    }
}
