package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.LiftKind;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class OptionalMapStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var elements = ctx.getElements();

        final var optionalElement = elements.getTypeElement("java.util.Optional");
        if (optionalElement == null) {
            return Optional.empty();
        }

        final var erasedOptional = types.erasure(optionalElement.asType());

        if (!(sourceType instanceof DeclaredType) || !(targetType instanceof DeclaredType)) {
            return Optional.empty();
        }

        if (!types.isSameType(types.erasure(sourceType), erasedOptional)
                || !types.isSameType(types.erasure(targetType), erasedOptional)) {
            return Optional.empty();
        }

        final var sourceArgs = ((DeclaredType) sourceType).getTypeArguments();
        final var targetArgs = ((DeclaredType) targetType).getTypeArguments();

        if (sourceArgs.isEmpty() || targetArgs.isEmpty()) {
            return Optional.empty();
        }

        final var sourceElementType = sourceArgs.get(0);
        final var targetElementType = targetArgs.get(0);

        if (types.isSameType(sourceElementType, targetElementType)) {
            return Optional.empty();
        }

        return Optional.of(new TransformProposal(
                sourceType,
                targetType,
                input -> CodeBlock.of("$L.map(e -> e)", input),
                this,
                LiftKind.OPTIONAL,
                sourceElementType,
                targetElementType));
    }
}
