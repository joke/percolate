package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import lombok.experimental.UtilityClass;

@UtilityClass
class Members {

    private static final String OBJECT_FQN = "java.lang.Object";

    static Iterable<? extends Element> declaredMembersOf(final TypeElement typeElement, final ResolveCtx ctx) {
        return ctx.elements().getAllMembers(typeElement);
    }

    static boolean isInObjectClass(final Element member) {
        final var enclosing = member.getEnclosingElement();
        return enclosing instanceof TypeElement
                && ((TypeElement) enclosing).getQualifiedName().contentEquals(OBJECT_FQN);
    }
}
