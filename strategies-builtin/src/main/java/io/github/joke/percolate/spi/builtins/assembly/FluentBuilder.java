package io.github.joke.percolate.spi.builtins.assembly;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

// The fluent convention: a static no-arg builder() on the target, setters named exactly after the declared child,
// and a no-arg build(). Covers Lombok @Builder, AutoValue, and most hand-written builders. Everything structural
// lives in BuilderAssembly; this class is only the convention.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluentBuilder extends BuilderAssembly {

    private static final String ENTRY = "builder";

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> builderFor(final TypeElement targetElement, final ResolveCtx ctx) {
        return staticFactoryBuilder(targetElement, ENTRY, ctx);
    }

    @Override
    @VisibleForTesting
    protected CodeBlock entryCall(final TypeElement targetElement, final TypeElement builderElement) {
        return CodeBlock.of("$T.$N()", ClassName.get(targetElement), ENTRY);
    }

    @Override
    @VisibleForTesting
    protected String labelHead(final TypeElement targetElement, final TypeElement builderElement) {
        return targetElement.getSimpleName() + "." + ENTRY;
    }
}
