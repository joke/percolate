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

import static io.github.joke.percolate.spi.Weights.STEP_GETTER;
import static java.lang.Character.toUpperCase;
import static javax.lang.model.type.TypeKind.BOOLEAN;

// Resolves one source-path segment to a JavaBeans getter (getX() / boolean isX()) on the parent type, on the
// Accessor archetype base: candidate-free, the base pins the parent and the segment and wires the one-port
// accessor io.github.joke.percolate.spi.OperationSpec; this strategy supplies only the getter match and its
// parent.getX() rendering. The produced value's nullness is the getter's, resolved through the demand oracle by
// the base.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class GetterPathResolver extends Accessor {

    @Override
    @VisibleForTesting
    protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx ctx) {
        final var getterName = "get" + capitalize(segment);
        final var isName = "is" + capitalize(segment);
        return Members.declaredMembersOf(parent, ctx)
                .flatMap(member ->
                        matchGetter(member, getterName, ctx).or(() -> matchBooleanIs(member, isName, ctx)).stream())
                .findFirst()
                .map(this::step);
    }

    Step step(final ExecutableElement method) {
        final var methodName = method.getSimpleName().toString();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N()", inputs.single(), methodName);
        return new Step(method.getReturnType(), method, methodName + "()", STEP_GETTER, codegen);
    }

    Optional<ExecutableElement> matchGetter(final Element member, final String getterName, final ResolveCtx ctx) {
        return Members.noArgMethodNamed(member, getterName, ctx);
    }

    Optional<ExecutableElement> matchBooleanIs(final Element member, final String isName, final ResolveCtx ctx) {
        return Members.noArgMethodNamed(member, isName, ctx).filter(method -> isBooleanReturn(method, ctx));
    }

    boolean isBooleanReturn(final ExecutableElement method, final ResolveCtx ctx) {
        final var returnType = method.getReturnType();
        return ctx.kind(returnType) == BOOLEAN || "java.lang.Boolean".equals(ctx.qualifiedName(returnType));
    }

    // One return, so the empty case is not a second return statement indistinguishable from returning "".
    String capitalize(final String segment) {
        return segment.isEmpty() ? segment : toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
