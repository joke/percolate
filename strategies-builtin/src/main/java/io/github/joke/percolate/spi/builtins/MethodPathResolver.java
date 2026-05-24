package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ResolvedSegment;
import io.github.joke.percolate.spi.Weights;
import lombok.NoArgsConstructor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

@AutoService(PathSegmentResolver.class)
@NoArgsConstructor
public final class MethodPathResolver implements PathSegmentResolver {

    @Override
    public Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
        final var typeElement = Members.asTypeElement(parentType, ctx);
        if (typeElement.isEmpty()) {
            return Optional.empty();
        }
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var match = matchAccessor(member, segment);
            if (match.isPresent()) {
                return Optional.of(buildResolved(match.get(), segment));
            }
        }
        return Optional.empty();
    }

    private Optional<ExecutableElement> matchAccessor(final Element candidate, final String segment) {
        if (candidate.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) candidate;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        return method.getSimpleName().contentEquals(segment) ? Optional.of(method) : Optional.empty();
    }

    private ResolvedSegment buildResolved(final ExecutableElement method, final String segment) {
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), segment);
        return new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_METHOD);
    }
}
