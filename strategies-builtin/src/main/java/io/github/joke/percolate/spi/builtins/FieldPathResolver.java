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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a visible (non-private, non-static) field on a candidate (parent) type,
 * emitting a one-port {@link OperationSpec} typed to the field's type. The operation renders {@code parent.field};
 * the produced value's nullness is the field's, resolved through the demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FieldPathResolver implements ExpansionStrategy {

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
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var match = matchField(member, segment);
            if (match.isPresent()) {
                return Stream.of(buildSpec(match.get(), segment, parentType, demand));
            }
        }
        return Stream.empty();
    }

    private Optional<VariableElement> matchField(final Element candidate, final String segment) {
        if (candidate.getKind() != ElementKind.FIELD) {
            return Optional.empty();
        }
        if (!candidate.getSimpleName().contentEquals(segment)) {
            return Optional.empty();
        }
        final var modifiers = candidate.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            return Optional.empty();
        }
        return Optional.of((VariableElement) candidate);
    }

    private OperationSpec buildSpec(
            final VariableElement field, final String segment, final TypeMirror parentType, final Demand demand) {
        final OperationCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N", inputs.single(), segment);
        final var port = new Port("value", parentType, Nullability.NON_NULL);
        final var fieldType = field.asType();
        final var outputNullness = demand.nullnessOf(fieldType, field);
        return OperationSpec.of(codegen, Weights.STEP_FIELD, List.of(port), fieldType, outputNullness);
    }
}
