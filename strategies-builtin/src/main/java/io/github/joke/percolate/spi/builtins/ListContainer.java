package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/** The {@code java.util.List} sequence container: candidacy + stream codegen in one class. */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ListContainer extends CollectionContainer {

    @Override
    protected boolean matches(final TypeMirror type, final ResolveCtx ctx) {
        return Containers.isList(type, ctx);
    }

    @Override
    protected TypeMirror element(final TypeMirror type) {
        return Containers.typeArgument(type, 0);
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
