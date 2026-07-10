package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.experimental.UtilityClass;

@UtilityClass
class Members {

    private static final String OBJECT_FQN = "java.lang.Object";

    static Stream<? extends Element> declaredMembersOf(final TypeElement typeElement, final ResolveCtx ctx) {
        return ctx.membersOf(typeElement);
    }

    static boolean isInObjectClass(final Element member) {
        final var enclosing = member.getEnclosingElement();
        return enclosing instanceof TypeElement
                && ((TypeElement) enclosing).getQualifiedName().contentEquals(OBJECT_FQN);
    }

    /** {@code member} viewed as a declared (non-{@code Object}), zero-parameter method, else empty. */
    static Optional<ExecutableElement> asNoArgMethod(final Element member, final ResolveCtx ctx) {
        if (!ctx.isMethod(member)) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
        return isInObjectClass(method) || !method.getParameters().isEmpty() ? Optional.empty() : Optional.of(method);
    }

    /** A zero-parameter, non-{@code Object} method named exactly {@code name}, else empty. */
    static Optional<ExecutableElement> noArgMethodNamed(final Element member, final String name, final ResolveCtx ctx) {
        return asNoArgMethod(member, ctx)
                .filter(method -> method.getSimpleName().contentEquals(name));
    }
}
