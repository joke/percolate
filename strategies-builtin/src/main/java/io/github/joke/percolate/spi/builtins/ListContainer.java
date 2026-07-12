package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/** The {@code java.util.List} sequence container: candidacy + stream codegen in one class. */
@AutoService({ExpansionStrategy.class, SourceProjection.class})
@NoArgsConstructor
public final class ListContainer extends CollectionContainer {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isList(type);
    }

    @Override
    protected TypeMirror element(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.typeArgument(type, 0);
    }

    @Override
    protected CodeBlock collector() {
        return CodeBlock.of("$T.toList()", Collectors.class);
    }

    @Override
    protected Class<?> factoryType() {
        return List.class;
    }
}
