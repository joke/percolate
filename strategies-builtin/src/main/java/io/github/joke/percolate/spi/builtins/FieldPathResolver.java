package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ResolvedSegment;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(PathSegmentResolver.class)
@NoArgsConstructor
public final class FieldPathResolver implements PathSegmentResolver {

    @Override
    public Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
        if (parentType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        final var element = ctx.types().asElement(parentType);
        if (!(element instanceof TypeElement)) {
            return Optional.empty();
        }
        final var typeElement = (TypeElement) element;
        for (final var member : ctx.elements().getAllMembers(typeElement)) {
            final var match = matchField(member, segment);
            if (match.isPresent()) {
                return Optional.of(buildResolved(match.get(), segment));
            }
        }
        return Optional.empty();
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

    private ResolvedSegment buildResolved(final VariableElement field, final String segment) {
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N", inputs.single(), segment);
        return new ResolvedSegment(field.asType(), codegen, Weights.STEP);
    }
}
