package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.WrapperContainer;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The {@code java.util.Optional} presence container. Its {@link #iterate} yields a 0-or-1 element stream
 * ({@code Optional.stream()}), which is how the composer drops empties via a flat-map; {@link #wrap} lifts a
 * scalar via {@code ofNullable}; {@link #unwrap} collapses under the target's nullability.
 */
@AutoService(Bridge.class)
@NoArgsConstructor
public final class OptionalContainer extends WrapperContainer {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return Containers.isOptional(type, ctx);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.typeArgument(type, 0);
    }

    @Override
    protected Optional<TypeMirror> wrapped(final TypeMirror element, final ResolveCtx ctx) {
        final var optional = ctx.elements().getTypeElement("java.util.Optional");
        if (optional == null) {
            return Optional.empty();
        }
        return Optional.of(ctx.types().getDeclaredType(optional, element));
    }

    @Override
    public CodeBlock iterate(final CodeBlock container) {
        return CodeBlock.of("$L.stream()", container);
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
        return CodeBlock.of("$L.findFirst()", stream);
    }

    @Override
    public CodeBlock mapPresence(final CodeBlock wrapper, final String var, final CodeBlock body) {
        return CodeBlock.of("$L.map($N -> $L)", wrapper, var, body);
    }

    @Override
    public CodeBlock wrap(final CodeBlock scalar) {
        return CodeBlock.of("$T.ofNullable($L)", Optional.class, scalar);
    }

    @Override
    public CodeBlock unwrap(final CodeBlock wrapper, final Nullability targetNullability) {
        if (targetNullability == Nullability.NULLABLE) {
            return CodeBlock.of("$L.orElse(null)", wrapper);
        }
        return CodeBlock.of("$L.orElseThrow()", wrapper);
    }
}
