package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class CollectToSetStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(final TypeMirror sourceType, final TypeMirror targetType,
            final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var elements = ctx.getElements();

        if (!(targetType instanceof DeclaredType)) {
            return Optional.empty();
        }

        final var setElement = elements.getTypeElement("java.util.Set");
        if (setElement == null) {
            return Optional.empty();
        }

        final var erasedTarget = types.erasure(targetType);
        if (!types.isSameType(erasedTarget, types.erasure(setElement.asType()))) {
            return Optional.empty();
        }

        final var targetDeclared = (DeclaredType) targetType;
        if (targetDeclared.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }

        final var elementType = targetDeclared.getTypeArguments().get(0);
        final var streamElement = elements.getTypeElement("java.util.stream.Stream");
        if (streamElement == null) {
            return Optional.empty();
        }

        final var streamType = types.getDeclaredType(streamElement, elementType);

        return Optional.of(new TransformProposal(
                streamType,
                targetType,
                input -> CodeBlock.of("$L.collect($T.toSet())", input, Collectors.class),
                this));
    }
}
