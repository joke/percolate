package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
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
}
