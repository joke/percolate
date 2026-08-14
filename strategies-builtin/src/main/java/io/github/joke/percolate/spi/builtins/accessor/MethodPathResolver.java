package io.github.joke.percolate.spi.builtins.accessor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Accessor;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Weights.STEP_METHOD;
import static io.github.joke.percolate.spi.builtins.accessor.Members.declaredMembersOf;
import static io.github.joke.percolate.spi.builtins.accessor.Members.noArgMethodNamed;

// Resolves one source-path segment to a no-arg accessor method whose name equals the segment (a fluent
// accessor, e.g. value()) on the parent type, on the Accessor archetype base: candidate-free, the base pins the
// parent and the segment and wires the one-port accessor io.github.joke.percolate.spi.OperationSpec; this
// strategy supplies only the method match and its parent.value() rendering. The produced value's nullness is
// the method's, resolved through the demand oracle.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodPathResolver extends Accessor {

    @Override
    @VisibleForTesting
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        return declaredMembersOf(parent, ctx)
                .flatMap(member -> matchAccessor(member, segment, ctx).stream())
                .findFirst()
                .map(method -> step(method, segment));
    }

    @VisibleForTesting
    Step step(final ExecutableElement method, final String segment) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N()", inputs.single(), segment);
        return new Step(method.getReturnType(), method, segment + "()", STEP_METHOD, codegen);
    }

    @VisibleForTesting
    Optional<ExecutableElement> matchAccessor(final Element member, final String segment, final ResolveCtx ctx) {
        return noArgMethodNamed(member, segment, ctx);
    }
}
