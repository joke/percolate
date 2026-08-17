package io.github.joke.percolate.spi.builtins.assembly;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

// The side-located convention: the builder is a separate type named <Target>Builder sitting beside the target
// rather than nested in it, constructed directly (new MyClassBuilder()), with setters named after the declared
// child and a no-arg build().
//
// The name is only where the search STARTS — the match is decided structurally, so a same-named type that is
// private, has no accessible no-arg constructor, or carries no build() producing the target simply does not
// match, and even a wrong match still has to pass the containment gate.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class SideLocatedBuilder extends BuilderAssembly {

    private static final String SUFFIX = "Builder";

    @Override
    @VisibleForTesting
    protected Optional<TypeElement> builderFor(final TypeElement targetElement, final ResolveCtx ctx) {
        return Optional.ofNullable(ctx.typeElementNamed(builderName(targetElement)))
                .filter(builder -> !ctx.isPrivate(builder))
                .filter(builder -> hasNoArgConstructor(builder, ctx));
    }

    // The sibling builder's fully-qualified name: the target's own, suffixed. A nested target yields
    // com.example.Outer.InnerBuilder, which is exactly where a side-located builder for it would sit.
    @VisibleForTesting
    String builderName(final TypeElement targetElement) {
        return targetElement.getQualifiedName() + SUFFIX;
    }

    @VisibleForTesting
    boolean hasNoArgConstructor(final TypeElement builderElement, final ResolveCtx ctx) {
        return ctx.membersOf(builderElement).anyMatch(member -> isNoArgConstructor(member, ctx));
    }

    @VisibleForTesting
    boolean isNoArgConstructor(final Element member, final ResolveCtx ctx) {
        return ctx.isConstructor(member)
                && !ctx.isPrivate(member)
                && ((ExecutableElement) member).getParameters().isEmpty();
    }

    @Override
    @VisibleForTesting
    protected CodeBlock entryCall(final TypeElement targetElement, final TypeElement builderElement) {
        return CodeBlock.of("new $T()", ClassName.get(builderElement));
    }

    @Override
    @VisibleForTesting
    protected String labelHead(final TypeElement targetElement, final TypeElement builderElement) {
        return "new " + builderElement.getSimpleName();
    }
}
