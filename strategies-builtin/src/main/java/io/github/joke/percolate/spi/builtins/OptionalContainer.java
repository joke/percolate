package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Container;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The {@code java.util.Optional} presence container. It supplies no {@code collect} — that absence is what makes its
 * kind a presence wrapper. {@link #iterate()} yields a 0-or-1 element stream ({@code Optional.stream()}), which is how
 * a flat-map drops empties; {@link #mapPresence()} maps the wrapped value ({@code opt.map}); {@link #wrap()} lifts a
 * scalar via {@code ofNullable}; {@link #unwrap()} collapses under the target's nullability.
 */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class OptionalContainer extends Container {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return Containers.isOptional(type, ctx);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.typeArgument(type, 0);
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$L.stream()", container));
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.ofNullable($L)", Optional.class, scalar));
    }

    @Override
    public Optional<UnwrapSnippet> unwrap() {
        return Optional.of((wrapper, targetNullability) -> targetNullability == Nullability.NULLABLE
                ? CodeBlock.of("$L.orElse(null)", wrapper)
                : CodeBlock.of("$L.orElseThrow()", wrapper));
    }

    @Override
    public Optional<ScopeCodegen> mapPresence() {
        return Optional.of((operand, var, body) -> CodeBlock.of("$L.map($N -> $L)", operand, var, body));
    }
}
