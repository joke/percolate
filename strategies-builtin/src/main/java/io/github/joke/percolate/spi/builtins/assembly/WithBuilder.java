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

// The with-style convention: a static no-arg builder() on the target, setters named withX, and a no-arg build().
// Common in older fluent-immutable codebases.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class WithBuilder extends BuilderAssembly {

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

    @Override
    @VisibleForTesting
    protected String setterName(final String child) {
        return prefixed("with", child);
    }
}
