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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a visible (non-private, non-static) field on the parent type, on the
 * {@link Accessor} archetype base: candidate-free, the base pins the parent and the segment and wires the one-port
 * accessor {@link io.github.joke.percolate.spi.OperationSpec}; this strategy supplies only the field match and its
 * {@code parent.field} rendering. The produced value's nullness is the field's, resolved through the demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FieldPathResolver extends Accessor {

    @Override
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        for (final var member : Members.declaredMembersOf(parent, ctx)) {
            final var field = matchField(member, segment);
            if (field.isPresent()) {
                return Optional.of(step(field.get(), segment));
            }
        }
        return Optional.empty();
    }

    private static Step step(final VariableElement field, final String segment) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N", inputs.single(), segment);
        return new Step(field.asType(), field, "." + segment, Weights.STEP_FIELD, codegen);
    }

    private Optional<VariableElement> matchField(final Element member, final String segment) {
        if (member.getKind() != ElementKind.FIELD) {
            return Optional.empty();
        }
        if (!member.getSimpleName().contentEquals(segment)) {
            return Optional.empty();
        }
        final var modifiers = member.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            return Optional.empty();
        }
        return Optional.of((VariableElement) member);
    }
}
