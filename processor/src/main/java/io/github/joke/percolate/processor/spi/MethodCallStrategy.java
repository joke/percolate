package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class MethodCallStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();

        return ctx.getElements().getAllMembers(ctx.getMapperType()).stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> m.getReturnType().getKind() != TypeKind.VOID)
                .filter(m -> !Objects.equals(m, ctx.getCurrentMethod()))
                .filter(m ->
                        types.isAssignable(sourceType, m.getParameters().get(0).asType()))
                .filter(m -> types.isAssignable(m.getReturnType(), targetType))
                .findFirst()
                .map(m -> new TransformProposal(
                        sourceType, targetType, input -> CodeBlock.of("$L($L)", m.getSimpleName(), input), this));
    }
}
