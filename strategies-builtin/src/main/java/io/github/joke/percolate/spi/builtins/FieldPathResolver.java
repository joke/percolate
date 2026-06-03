package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Frontier;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
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
 * emitting a one-slot {@link io.github.joke.percolate.spi.Intent#BOUNDARY} step typed to the field's type. The
 * realised edge renders {@code parent.field}.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FieldPathResolver implements ExpansionStrategy {

    @Override
    public Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        return Segments.single(frontier)
                .map(segment ->
                        frontier.candidates().stream().flatMap(candidate -> resolve(candidate.getType(), segment, ctx)))
                .orElseGet(Stream::empty);
    }

    private Stream<ExpansionStep> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
        final var typeElement = Members.asTypeElement(parentType, ctx);
        if (typeElement.isEmpty()) {
            return Stream.empty();
        }
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var match = matchField(member, segment);
            if (match.isPresent()) {
                return Stream.of(buildStep(match.get(), segment, parentType));
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

    private ExpansionStep buildStep(final VariableElement field, final String segment, final TypeMirror parentType) {
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N", inputs.single(), segment);
        final var slot = new Slot("value", parentType, Weights.STEP_FIELD, field);
        return ExpansionStep.boundary(List.of(slot), field.asType(), codegen, Weights.STEP_FIELD);
    }
}
