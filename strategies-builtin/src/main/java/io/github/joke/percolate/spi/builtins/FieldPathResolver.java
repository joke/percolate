package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Accessor;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import javax.lang.model.element.Element;
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
        return Members.declaredMembersOf(parent, ctx)
                .flatMap(member -> matchField(member, segment, ctx).stream())
                .findFirst()
                .map(field -> step(field, segment));
    }

    static Step step(final VariableElement field, final String segment) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N", inputs.single(), segment);
        return new Step(field.asType(), field, "." + segment, Weights.STEP_FIELD, codegen);
    }

    Optional<VariableElement> matchField(final Element member, final String segment, final ResolveCtx ctx) {
        if (!isVisibleField(member, ctx)) {
            return Optional.empty();
        }
        return member.getSimpleName().contentEquals(segment) ? Optional.of((VariableElement) member) : Optional.empty();
    }

    boolean isVisibleField(final Element member, final ResolveCtx ctx) {
        return ctx.isField(member) && !ctx.isPrivate(member) && !ctx.isStatic(member);
    }
}
