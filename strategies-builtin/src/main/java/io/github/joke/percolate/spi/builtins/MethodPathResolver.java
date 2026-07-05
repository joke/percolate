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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a no-arg accessor method whose name equals the segment (a fluent accessor, e.g.
 * {@code value()}) on the parent type, on the {@link Accessor} archetype base: candidate-free, the base pins the parent
 * and the segment and wires the one-port accessor {@link io.github.joke.percolate.spi.OperationSpec}; this strategy
 * supplies only the method match and its {@code parent.value()} rendering. The produced value's nullness is the
 * method's, resolved through the demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodPathResolver extends Accessor {

    @Override
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        return Members.declaredMembersOf(parent, ctx)
                .flatMap(member -> matchAccessor(member, segment, ctx).stream())
                .findFirst()
                .map(method -> step(method, segment));
    }

    private static Step step(final ExecutableElement method, final String segment) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N()", inputs.single(), segment);
        return new Step(method.getReturnType(), method, segment + "()", Weights.STEP_METHOD, codegen);
    }

    private Optional<ExecutableElement> matchAccessor(
            final Element member, final String segment, final ResolveCtx ctx) {
        if (!ctx.isMethod(member)) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        return method.getSimpleName().contentEquals(segment) ? Optional.of(method) : Optional.empty();
    }
}
