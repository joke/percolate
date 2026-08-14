package io.github.joke.percolate.spi.builtins.accessor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Accessor;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Weights.STEP_FIELD;
import static io.github.joke.percolate.spi.builtins.accessor.Members.declaredMembersOf;

// Resolves one source-path segment to a visible (non-private, non-static) field on the parent type, on the
// Accessor archetype base: candidate-free, the base pins the parent and the segment and wires the one-port
// accessor io.github.joke.percolate.spi.OperationSpec; this strategy supplies only the field match and its
// parent.field rendering. The produced value's nullness is the field's, resolved through the demand oracle.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FieldPathResolver extends Accessor {

    @Override
    @VisibleForTesting
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        return declaredMembersOf(parent, ctx)
                .flatMap(member -> matchField(member, segment, ctx).stream())
                .findFirst()
                .map(field -> step(field, segment));
    }

    @VisibleForTesting
    Step step(final VariableElement field, final String segment) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N", inputs.single(), segment);
        return new Step(field.asType(), field, "." + segment, STEP_FIELD, codegen);
    }

    @VisibleForTesting
    Optional<VariableElement> matchField(final Element member, final String segment, final ResolveCtx ctx) {
        if (!isVisibleField(member, ctx)) {
            return Optional.empty();
        }
        return member.getSimpleName().contentEquals(segment) ? Optional.of((VariableElement) member) : Optional.empty();
    }

    @VisibleForTesting
    boolean isVisibleField(final Element member, final ResolveCtx ctx) {
        return ctx.isField(member) && !ctx.isPrivate(member) && !ctx.isStatic(member);
    }
}
