package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Accessor;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
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
        return Members.declaredMembersOf(parent, ctx)
                .flatMap(member ->
                        matchGetter(member, getterName, ctx).or(() -> matchBooleanIs(member, isName, ctx)).stream())
                .findFirst()
                .map(GetterPathResolver::step);
    }

    static Step step(final ExecutableElement method) {
        final var methodName = method.getSimpleName().toString();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N()", inputs.single(), methodName);
        return new Step(method.getReturnType(), method, methodName + "()", Weights.STEP_GETTER, codegen);
    }

    Optional<ExecutableElement> matchGetter(final Element member, final String getterName, final ResolveCtx ctx) {
        return Members.noArgMethodNamed(member, getterName, ctx);
    }

    Optional<ExecutableElement> matchBooleanIs(final Element member, final String isName, final ResolveCtx ctx) {
        return Members.noArgMethodNamed(member, isName, ctx).filter(method -> isBooleanReturn(method, ctx));
    }

    boolean isBooleanReturn(final ExecutableElement method, final ResolveCtx ctx) {
        final var returnType = method.getReturnType();
        return ctx.kind(returnType) == TypeKind.BOOLEAN || "java.lang.Boolean".equals(ctx.qualifiedName(returnType));
    }

    static String capitalize(final String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
