package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ResolvedSegment;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(PathSegmentResolver.class)
@NoArgsConstructor
public final class GetterPathResolver implements PathSegmentResolver {

    @Override
    public Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
        final var typeElement = Members.asTypeElement(parentType, ctx);
        if (typeElement.isEmpty()) {
            return Optional.empty();
        }
        final var getterName = "get" + capitalize(segment);
        final var isName = "is" + capitalize(segment);
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var getterMatch = matchGetter(member, getterName);
            if (getterMatch.isPresent()) {
                return Optional.of(buildResolved(getterMatch.get()));
            }
            final var isMatch = matchBooleanIs(member, isName);
            if (isMatch.isPresent()) {
                return Optional.of(buildResolved(isMatch.get()));
            }
        }
        return Optional.empty();
    }

    private Optional<ExecutableElement> matchGetter(final Element candidate, final String getterName) {
        if (candidate.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) candidate;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        return method.getSimpleName().contentEquals(getterName) ? Optional.of(method) : Optional.empty();
    }

    private Optional<ExecutableElement> matchBooleanIs(final Element candidate, final String isName) {
        if (candidate.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) candidate;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        if (!method.getSimpleName().contentEquals(isName)) {
            return Optional.empty();
        }
        return isBooleanReturn(method) ? Optional.of(method) : Optional.empty();
    }

    private boolean isBooleanReturn(final ExecutableElement method) {
        final var returnType = method.getReturnType();
        if (returnType.getKind() == TypeKind.BOOLEAN) {
            return true;
        }
        if (returnType.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var element = ((javax.lang.model.type.DeclaredType) returnType).asElement();
        return element instanceof TypeElement
                && ((TypeElement) element).getQualifiedName().contentEquals("java.lang.Boolean");
    }

    private ResolvedSegment buildResolved(final ExecutableElement method) {
        final var methodName = method.getSimpleName().toString();
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), methodName);
        return new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_GETTER, method);
    }

    private static String capitalize(final String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
