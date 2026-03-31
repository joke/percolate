package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.ElementConstraint;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class StreamMapStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var elements = ctx.getElements();

        final var streamElement = elements.getTypeElement("java.util.stream.Stream");
        if (streamElement == null) {
            return Optional.empty();
        }

        final var erasedStream = types.erasure(streamElement.asType());

        if (!(sourceType instanceof DeclaredType) || !(targetType instanceof DeclaredType)) {
            return Optional.empty();
        }

        if (!types.isSameType(types.erasure(sourceType), erasedStream)
                || !types.isSameType(types.erasure(targetType), erasedStream)) {
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

        final var constraint = new ElementConstraint(sourceElementType, targetElementType);

        return Optional.of(new TransformProposal(
                sourceType,
                targetType,
                input -> CodeBlock.of("$L.map(e -> e)", input),
                this,
                constraint,
                innerTemplate ->
                        input -> CodeBlock.of("$L.map(e -> $L)", input, innerTemplate.apply(CodeBlock.of("e")))));
    }
}
