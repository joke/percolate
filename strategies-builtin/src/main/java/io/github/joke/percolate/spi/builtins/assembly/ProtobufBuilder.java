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

// The protobuf convention: a static no-arg newBuilder() on the target, setters named setX, and a no-arg build().
// It differs from FluentBuilder on both naming axes, which is what proves the archetype is not secretly
// Lombok-shaped.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ProtobufBuilder extends BuilderAssembly {

    private static final String ENTRY = "newBuilder";

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
        return prefixed("set", child);
    }
}
