package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class StreamFromCollectionStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var elements = ctx.getElements();

        if (!(sourceType instanceof DeclaredType)) {
            return Optional.empty();
        }

        final var collectionType = elements.getTypeElement("java.util.Collection");
        final var iterableType = elements.getTypeElement("java.util.Iterable");

        if (collectionType == null && iterableType == null) {
            return Optional.empty();
        }

        final var erasedSource = types.erasure(sourceType);
        final var isCollection =
                collectionType != null && types.isAssignable(erasedSource, types.erasure(collectionType.asType()));
        final var isIterable =
                iterableType != null && types.isAssignable(erasedSource, types.erasure(iterableType.asType()));

        if (!isCollection && !isIterable) {
            return Optional.empty();
        }

        final var elementType = extractElementType((DeclaredType) sourceType, ctx);
        if (elementType.isEmpty()) {
            return Optional.empty();
        }

        final var streamElement = elements.getTypeElement("java.util.stream.Stream");
        if (streamElement == null) {
            return Optional.empty();
        }

        final var streamType = types.getDeclaredType(streamElement, elementType.get());

        final CodeTemplate template = isCollection
                ? input -> CodeBlock.of("$L.stream()", input)
                : input -> CodeBlock.of("$T.stream($L.spliterator(), false)", StreamSupport.class, input);

        return Optional.of(new TransformProposal(sourceType, streamType, template, this));
    }

    private static Optional<TypeMirror> extractElementType(
            final DeclaredType declaredType, final ResolutionContext ctx) {
        final var typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
            return Optional.of(typeArgs.get(0));
        }

        final var types = ctx.getTypes();
        final var elements = ctx.getElements();
        final var iterableElement = elements.getTypeElement("java.util.Iterable");
        if (iterableElement == null) {
            return Optional.empty();
        }

        for (final var supertype : types.directSupertypes(declaredType)) {
            if (supertype instanceof DeclaredType) {
                final var superDeclared = (DeclaredType) supertype;
                final var superElement = types.erasure(superDeclared);
                if (types.isSameType(superElement, types.erasure(iterableElement.asType()))
                        && !superDeclared.getTypeArguments().isEmpty()) {
                    return Optional.of(superDeclared.getTypeArguments().get(0));
                }
            }
        }

        return Optional.empty();
    }
}
