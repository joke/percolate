package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a JavaBeans getter ({@code getX()} / boolean {@code isX()}) on a candidate
 * (parent) type, emitting a one-port {@link OperationSpec} typed to the getter's return type. The driver feeds the
 * segment to descend via {@link Demand#directive()} and binds the port to the existing parent Value; the operation
 * renders {@code parent.getX()}. The produced value's nullness is the getter's, resolved through the demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class GetterPathResolver implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        return Segments.single(demand)
                .map(segment -> demand.candidates().stream()
                        .flatMap(candidate -> resolve(candidate.getType(), segment, demand, ctx)))
                .orElseGet(Stream::empty);
    }

    private Stream<OperationSpec> resolve(
            final TypeMirror parentType, final String segment, final Demand demand, final ResolveCtx ctx) {
        final var typeElement = Members.asTypeElement(parentType, ctx);
        if (typeElement.isEmpty()) {
            return Stream.empty();
        }
        final var getterName = "get" + capitalize(segment);
        final var isName = "is" + capitalize(segment);
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var getterMatch = matchGetter(member, getterName);
            if (getterMatch.isPresent()) {
                return Stream.of(buildSpec(getterMatch.get(), parentType, demand));
            }
            final var isMatch = matchBooleanIs(member, isName);
            if (isMatch.isPresent()) {
                return Stream.of(buildSpec(isMatch.get(), parentType, demand));
            }
        }
        return Stream.empty();
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

    private OperationSpec buildSpec(final ExecutableElement method, final TypeMirror parentType, final Demand demand) {
        final var methodName = method.getSimpleName().toString();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N()", inputs.single(), methodName);
        final var port = new Port("value", parentType, Nullability.NON_NULL);
        final var returnType = method.getReturnType();
        final var outputNullness = demand.nullnessOf(returnType, method);
        return OperationSpec.of(
                methodName + "()", codegen, Weights.STEP_GETTER, List.of(port), returnType, outputNullness);
    }

    private static String capitalize(final String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
