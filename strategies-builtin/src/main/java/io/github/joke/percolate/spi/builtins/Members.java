package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

@UtilityClass
class Members {

    private static final String OBJECT_FQN = "java.lang.Object";

    static Optional<TypeElement> asTypeElement(final TypeMirror parentType, final ResolveCtx ctx) {
        if (parentType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        final var element = ctx.types().asElement(parentType);
        if (!(element instanceof TypeElement)) {
            return Optional.empty();
        }
        return Optional.of((TypeElement) element);
    }

    static Iterable<? extends Element> declaredMembersOf(final TypeElement typeElement, final ResolveCtx ctx) {
        return ctx.elements().getAllMembers(typeElement);
    }

    static boolean isInObjectClass(final Element member) {
        final var enclosing = member.getEnclosingElement();
        return enclosing instanceof TypeElement
                && ((TypeElement) enclosing).getQualifiedName().contentEquals(OBJECT_FQN);
    }
}
