package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class OptionalWrapStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var elements = ctx.getElements();

        final var optionalElement = elements.getTypeElement("java.util.Optional");
        if (optionalElement == null) {
            return Optional.empty();
        }

        if (!(targetType instanceof DeclaredType)) {
            return Optional.empty();
        }

        final var erasedOptional = types.erasure(optionalElement.asType());
        if (!types.isSameType(types.erasure(targetType), erasedOptional)) {
            return Optional.empty();
        }

        if (sourceType instanceof DeclaredType && types.isSameType(types.erasure(sourceType), erasedOptional)) {
            return Optional.empty();
        }

        final var targetArgs = ((DeclaredType) targetType).getTypeArguments();
        if (targetArgs.isEmpty()) {
            return Optional.empty();
        }

        final var elementType = targetArgs.get(0);

        return Optional.of(new TransformProposal(
                elementType, targetType, input -> CodeBlock.of("$T.of($L)", Optional.class, input), this));
    }
}
