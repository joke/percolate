package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Accessor;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a JavaBeans getter ({@code getX()} / boolean {@code isX()}) on the parent type,
 * on the {@link Accessor} archetype base: candidate-free, the base pins the parent and the segment and wires the
 * one-port accessor {@link io.github.joke.percolate.spi.OperationSpec}; this strategy supplies only the getter match
 * and its {@code parent.getX()} rendering. The produced value's nullness is the getter's, resolved through the demand
 * oracle by the base.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class GetterPathResolver extends Accessor {

    @Override
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        final var getterName = "get" + capitalize(segment);
        final var isName = "is" + capitalize(segment);
        for (final var member : Members.declaredMembersOf(parent, ctx)) {
            final var getter = matchGetter(member, getterName);
            if (getter.isPresent()) {
                return Optional.of(step(getter.get()));
            }
            final var booleanIs = matchBooleanIs(member, isName);
            if (booleanIs.isPresent()) {
                return Optional.of(step(booleanIs.get()));
            }
        }
        return Optional.empty();
    }

    private static Step step(final ExecutableElement method) {
        final var methodName = method.getSimpleName().toString();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N()", inputs.single(), methodName);
        return new Step(method.getReturnType(), method, methodName + "()", Weights.STEP_GETTER, codegen);
    }

    private Optional<ExecutableElement> matchGetter(final Element member, final String getterName) {
        if (member.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        return method.getSimpleName().contentEquals(getterName) ? Optional.of(method) : Optional.empty();
    }

    private Optional<ExecutableElement> matchBooleanIs(final Element member, final String isName) {
        if (member.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
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
        final var element = ((DeclaredType) returnType).asElement();
        return element instanceof TypeElement
                && ((TypeElement) element).getQualifiedName().contentEquals("java.lang.Boolean");
    }

    private static String capitalize(final String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
