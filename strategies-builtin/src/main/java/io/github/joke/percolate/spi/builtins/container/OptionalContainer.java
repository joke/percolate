package io.github.joke.percolate.spi.builtins.container;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Nullability.NULLABLE;

// The java.util.Optional presence container. It supplies no collect — that absence is what makes its kind a
// presence wrapper. .iterate() yields a 0-or-1 element stream (Optional.stream()), which is how a flat-map
// drops empties; .mapPresence() maps the wrapped value (opt.map) as a functor lift; .wrap() lifts a scalar via
// ofNullable; .unwrap() collapses under the target's nullability.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class OptionalContainer extends StreamContainer {

    @Override
    @VisibleForTesting
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isOptional(type);
    }

    @Override
    @VisibleForTesting
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> kindErasure(final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed("java.util.Optional"));
    }

    @Override
    public Optional<UnarySnippet> iterate() {
        return Optional.of(container -> CodeBlock.of("$L$Z.stream()", container));
    }

    @Override
    public Optional<UnarySnippet> wrap() {
        return Optional.of(scalar -> CodeBlock.of("$T.ofNullable($L)", Optional.class, scalar));
    }

    @Override
    @VisibleForTesting
    protected Nullability wrapNullness() {
        return NULLABLE;
    }

    @Override
    public Optional<UnwrapSnippet> unwrap() {
        return Optional.of((wrapper, targetNullability) -> targetNullability == NULLABLE
                ? CodeBlock.of("$L$Z.orElse(null)", wrapper)
                : CodeBlock.of("$L$Z.orElseThrow()", wrapper));
    }

    @Override
    public Optional<ScopeCodegen> mapPresence() {
        return Optional.of((operand, var, body) -> CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body));
    }
}
