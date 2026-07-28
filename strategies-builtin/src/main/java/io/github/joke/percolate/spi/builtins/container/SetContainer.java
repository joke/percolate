package io.github.joke.percolate.spi.builtins.container;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

// The java.util.Set sequence container: candidacy + stream codegen in one class.
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class SetContainer extends CollectionContainer {

    @Override
    @VisibleForTesting
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isSet(type);
    }

    @Override
    @VisibleForTesting
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    @VisibleForTesting
    protected CodeBlock collector() {
        return CodeBlock.of("$T.toSet()", Collectors.class);
    }

    @Override
    @VisibleForTesting
    protected Class<?> factoryType() {
        return Set.class;
    }
}
